import { useState, useRef, useEffect } from 'react';
import { InputTextarea } from 'primereact/inputtextarea';
import '../styles/autocomplete-textarea.css';

const AutocompleteTextarea = ({ 
  value, 
  onChange, 
  onKeyDown, 
  placeholder, 
  className, 
  disabled, 
  rows,
  autoResize,
  tools = [] 
}) => {
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [filteredSuggestions, setFilteredSuggestions] = useState([]);
  const [selectedIndex, setSelectedIndex] = useState(-1);
  const [cursorPosition, setCursorPosition] = useState(0);
  const [autocompletePrefix, setAutocompletePrefix] = useState('');
  
  const textareaRef = useRef(null);
  const suggestionsPanelRef = useRef(null);
  
  // Parse current context (tool vs parameter vs value)
  const parseContext = (inputValue, cursorPos) => {
    const beforeCursor = inputValue.slice(0, cursorPos);
    const lines = beforeCursor.split('\n');
    const currentLine = lines[lines.length - 1];
    
    // Check if we're typing a tool name (beginning of line or after whitespace)
    const toolNamePattern = /^\s*(\w*)$/;
    const toolNameMatch = currentLine.match(toolNamePattern);
    
    if (toolNameMatch) {
      return {
        type: 'tool',
        prefix: toolNameMatch[1],
        toolName: null,
        paramName: null
      };
    }
    
    // Check if we're typing parameters after a tool name
    const toolWithParamsPattern = /^\s*(\w+)\s+(.*)$/;
    const toolWithParamsMatch = currentLine.match(toolWithParamsPattern);
    
    if (toolWithParamsMatch) {
      const toolName = toolWithParamsMatch[1];
      const afterTool = toolWithParamsMatch[2];
      
      // Check if tool exists
      const tool = tools.find(t => t.name === toolName);
      if (tool) {
        // Parse parameter being typed
        const paramPattern = /(\w+):?(\w*)$/;
        const paramMatch = afterTool.match(paramPattern);
        
        if (paramMatch) {
          const paramName = paramMatch[1];
          const paramValue = paramMatch[2] || '';
          
          // Check if we're typing parameter value (after colon)
          if (afterTool.includes(':') && !afterTool.endsWith(' ')) {
            return {
              type: 'parameter_value',
              prefix: paramValue,
              toolName: toolName,
              paramName: paramName,
              tool: tool
            };
          } else {
            return {
              type: 'parameter',
              prefix: paramName,
              toolName: toolName,
              paramName: null,
              tool: tool
            };
          }
        }
        
        return {
          type: 'parameter',
          prefix: afterTool.trim(),
          toolName: toolName,
          paramName: null,
          tool: tool
        };
      }
    }
    
    return {
      type: 'tool',
      prefix: currentLine.trim(),
      toolName: null,
      paramName: null
    };
  };

  // Get contextual suggestions based on current input
  const getContextualSuggestions = (context) => {
    const suggestions = [];
    
    switch (context.type) {
      case 'tool':
        // Suggest tool names
        tools.forEach(tool => {
          if (tool.name.toLowerCase().includes(context.prefix.toLowerCase())) {
            suggestions.push({
              text: tool.name,
              type: 'tool',
              description: tool.description,
              detail: `Tool: ${tool.name}`,
              insertText: tool.name + ' '
            });
          }
        });
        break;
        
      case 'parameter':
        // Suggest parameters for the current tool
        if (context.tool && context.tool.inputSchema?.properties) {
          Object.entries(context.tool.inputSchema.properties).forEach(([paramName, paramSchema]) => {
            if (paramName.toLowerCase().includes(context.prefix.toLowerCase())) {
              const isRequired = context.tool.inputSchema.required?.includes(paramName);
              suggestions.push({
                text: paramName,
                type: 'parameter',
                description: paramSchema.description || `Parameter: ${paramName}`,
                detail: `${paramSchema.type || 'any'}${isRequired ? ' (required)' : ' (optional)'}`,
                insertText: paramName + ':'
              });
            }
          });
        }
        break;
        
      case 'parameter_value':
        // Suggest parameter values based on parameter type
        if (context.tool && context.tool.inputSchema?.properties?.[context.paramName]) {
          const paramSchema = context.tool.inputSchema.properties[context.paramName];
          const valuesSuggestions = getParameterValueSuggestions(context.paramName, paramSchema, context.prefix);
          suggestions.push(...valuesSuggestions);
        }
        break;
    }
    
    return suggestions;
  };
  
  // Get parameter value suggestions based on parameter type and schema
  const getParameterValueSuggestions = (paramName, paramSchema, prefix) => {
    const suggestions = [];
    
    // Common example values for different parameter types
    const commonValues = {
      'toolset': ['github', 'docker', 'npm', 'python', 'git'],
      'repository': ['owner/repo', 'accentureshark/mentor', 'microsoft/vscode'],
      'owner': ['github', 'microsoft', 'google', 'facebook'],
      'repo': ['mentor', 'vscode', 'react', 'typescript'],
      'path': ['README.md', 'src/main.js', 'package.json', '.gitignore'],
      'query': ['search term', 'example query', 'bug fix'],
      'type': ['public', 'private', 'all'],
      'language': ['javascript', 'python', 'java', 'typescript'],
      'file': ['index.js', 'app.py', 'Main.java', 'component.jsx'],
      'branch': ['main', 'develop', 'feature/branch', 'release/v1.0'],
      'tag': ['v1.0.0', 'latest', 'stable'],
      'issue_number': ['1', '42', '123'],
      'pull_number': ['1', '10', '25'],
      'status': ['open', 'closed', 'merged'],
      'state': ['open', 'closed', 'all']
    };
    
    // Check if parameter has enum values in schema
    if (paramSchema.enum) {
      paramSchema.enum.forEach(value => {
        if (value.toString().toLowerCase().includes(prefix.toLowerCase())) {
          suggestions.push({
            text: value,
            type: 'enum_value',
            description: `Enum value: ${value}`,
            detail: 'predefined value',
            insertText: value
          });
        }
      });
    }
    
    // Suggest common values based on parameter name
    const paramKey = paramName.toLowerCase();
    if (commonValues[paramKey]) {
      commonValues[paramKey].forEach(value => {
        if (value.toLowerCase().includes(prefix.toLowerCase())) {
          suggestions.push({
            text: value,
            type: 'common_value',
            description: `Example: ${value}`,
            detail: `common ${paramSchema.type || 'value'}`,
            insertText: value
          });
        }
      });
    }
    
    // Add type-based suggestions
    if (paramSchema.type === 'boolean') {
      ['true', 'false'].forEach(value => {
        if (value.includes(prefix.toLowerCase())) {
          suggestions.push({
            text: value,
            type: 'boolean_value',
            description: `Boolean: ${value}`,
            detail: 'boolean',
            insertText: value
          });
        }
      });
    }
    
    return suggestions;
  };



  // Find current word at cursor position
  const getCurrentWord = (text, position) => {
    const beforeCursor = text.slice(0, position);
    const afterCursor = text.slice(position);
    
    // Find word boundaries (space, newline, punctuation)
    const wordBoundaryRegex = /[\s\n.,!?;]/;
    
    let wordStart = position;
    while (wordStart > 0 && !wordBoundaryRegex.test(beforeCursor[wordStart - 1])) {
      wordStart--;
    }
    
    let wordEnd = position;
    while (wordEnd < text.length && !wordBoundaryRegex.test(afterCursor[wordEnd - position])) {
      wordEnd++;
    }
    
    return {
      word: text.slice(wordStart, wordEnd),
      start: wordStart,
      end: wordEnd
    };
  };

  // Filter suggestions based on current input context
  const updateSuggestions = (inputValue, cursorPos) => {
    const context = parseContext(inputValue, cursorPos);
    
    if (context.prefix.length < 1) {
      setShowSuggestions(false);
      setFilteredSuggestions([]);
      setAutocompletePrefix('');
      return;
    }

    const suggestions = getContextualSuggestions(context);
    const filtered = suggestions.slice(0, 10); // Limit to 10 suggestions

    if (filtered.length > 0) {
      setFilteredSuggestions(filtered);
      setShowSuggestions(true);
      setSelectedIndex(-1);
      setAutocompletePrefix(context.prefix);
    } else {
      setShowSuggestions(false);
      setFilteredSuggestions([]);
      setAutocompletePrefix('');
    }
  };

  // Handle input change
  const handleInputChange = (e) => {
    const newValue = e.target.value;
    const newCursorPos = e.target.selectionStart;
    
    setCursorPosition(newCursorPos);
    onChange(e);
    
    // Update suggestions based on new input
    updateSuggestions(newValue, newCursorPos);
  };

  // Handle key down events
  const handleKeyDown = (e) => {
    if (showSuggestions && filteredSuggestions.length > 0) {
      switch (e.key) {
        case 'ArrowDown':
          e.preventDefault();
          setSelectedIndex(prev => 
            prev < filteredSuggestions.length - 1 ? prev + 1 : 0
          );
          break;
        case 'ArrowUp':
          e.preventDefault();
          setSelectedIndex(prev => 
            prev > 0 ? prev - 1 : filteredSuggestions.length - 1
          );
          break;
        case 'Enter':
        case 'Tab':
          if (selectedIndex >= 0) {
            e.preventDefault();
            insertSuggestion(filteredSuggestions[selectedIndex]);
            return;
          }
          break;
        case 'Escape':
          e.preventDefault();
          setShowSuggestions(false);
          return;
      }
    }
    
    // Call the original onKeyDown handler
    if (onKeyDown) {
      onKeyDown(e);
    }
  };

  // Insert selected suggestion with proper context handling
  const insertSuggestion = (suggestion) => {
    const context = parseContext(value, cursorPosition);
    
    let newValue = value;
    let newCursorPos = cursorPosition;
    
    const beforeCursor = value.slice(0, cursorPosition);
    const afterCursor = value.slice(cursorPosition);
    const lines = beforeCursor.split('\n');
    const currentLine = lines[lines.length - 1];
    const afterCurrentLine = afterCursor.split('\n')[0] || '';
    
    // Find the position to replace based on context
    let replaceStart = cursorPosition;
    let replaceEnd = cursorPosition;
    
    switch (context.type) {
      case 'tool':
        // Replace the tool prefix
        replaceStart = cursorPosition - context.prefix.length;
        newValue = value.slice(0, replaceStart) + suggestion.insertText + value.slice(cursorPosition);
        newCursorPos = replaceStart + suggestion.insertText.length;
        break;
        
      case 'parameter':
        // Replace the parameter prefix
        const toolEnd = currentLine.indexOf(' ') + 1;
        const lineStart = beforeCursor.length - currentLine.length;
        replaceStart = lineStart + toolEnd + currentLine.substring(toolEnd).lastIndexOf(' ') + 1;
        if (replaceStart === lineStart + toolEnd) {
          replaceStart = lineStart + toolEnd;
        }
        
        newValue = value.slice(0, replaceStart) + suggestion.insertText + value.slice(cursorPosition);
        newCursorPos = replaceStart + suggestion.insertText.length;
        break;
        
      case 'parameter_value':
        // Replace the parameter value after colon
        const colonIndex = currentLine.lastIndexOf(':');
        if (colonIndex !== -1) {
          const lineStart2 = beforeCursor.length - currentLine.length;
          replaceStart = lineStart2 + colonIndex + 1;
          newValue = value.slice(0, replaceStart) + suggestion.insertText + value.slice(cursorPosition);
          newCursorPos = replaceStart + suggestion.insertText.length;
        }
        break;
        
      default:
        // Fallback to simple replacement
        const { start, end } = getCurrentWord(value, cursorPosition);
        newValue = value.slice(0, start) + suggestion.insertText + value.slice(end);
        newCursorPos = start + suggestion.insertText.length;
    }
    
    // Create a synthetic event
    const syntheticEvent = {
      target: {
        value: newValue,
        selectionStart: newCursorPos
      }
    };
    
    onChange(syntheticEvent);
    setShowSuggestions(false);
    
    // Focus back to textarea and set cursor position
    setTimeout(() => {
      if (textareaRef.current) {
        textareaRef.current.focus();
        textareaRef.current.setSelectionRange(newCursorPos, newCursorPos);
      }
    }, 0);
  };

  // Handle suggestion click
  const handleSuggestionClick = (suggestion) => {
    insertSuggestion(suggestion);
  };

  // Handle cursor position change
  const handleCursorPositionChange = (e) => {
    const newCursorPos = e.target.selectionStart;
    setCursorPosition(newCursorPos);
    updateSuggestions(value, newCursorPos);
  };

  // Close suggestions when clicking outside
  useEffect(() => {
    const handleClickOutside = (event) => {
      if (suggestionsPanelRef.current && 
          !suggestionsPanelRef.current.contains(event.target) &&
          !textareaRef.current?.contains(event.target)) {
        setShowSuggestions(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, []);

  return (
    <div className="autocomplete-textarea-container">
      <InputTextarea
        ref={textareaRef}
        value={value}
        onChange={handleInputChange}
        onKeyDown={handleKeyDown}
        onSelect={handleCursorPositionChange}
        placeholder={placeholder}
        className={className}
        disabled={disabled}
        rows={rows}
        autoResize={autoResize}
      />
      
      {showSuggestions && filteredSuggestions.length > 0 && (
        <div 
          ref={suggestionsPanelRef}
          className="autocomplete-suggestions"
        >
          <div className="autocomplete-header">
            <i className="pi pi-search" />
            <span>IDE-style suggestions</span>
          </div>
          <div className="autocomplete-list">
            {filteredSuggestions.map((suggestion, index) => (
              <div
                key={index}
                className={`autocomplete-item ${index === selectedIndex ? 'selected' : ''}`}
                onClick={() => handleSuggestionClick(suggestion)}
                onMouseEnter={() => setSelectedIndex(index)}
              >
                <div className="autocomplete-item-content">
                  <span className="autocomplete-item-text">
                    {suggestion.text}
                    {suggestion.type === 'tool' && <span className="suggestion-type-badge tool">🔧</span>}
                    {suggestion.type === 'parameter' && <span className="suggestion-type-badge param">📝</span>}
                    {(suggestion.type === 'enum_value' || suggestion.type === 'common_value' || suggestion.type === 'boolean_value') && <span className="suggestion-type-badge value">💡</span>}
                  </span>
                  <div className="autocomplete-item-details">
                    <small className="autocomplete-description">{suggestion.description}</small>
                    {suggestion.detail && (
                      <small className="autocomplete-detail">{suggestion.detail}</small>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
          <div className="autocomplete-footer">
            <small>↑↓ to navigate • Enter/Tab to select • Esc to close</small>
          </div>
        </div>
      )}
    </div>
  );
};

export default AutocompleteTextarea;