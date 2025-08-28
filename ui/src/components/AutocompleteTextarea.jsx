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
  
  // Extract tool names and descriptions for autocomplete
  const getToolSuggestions = () => {
    const suggestions = [];
    
    tools.forEach(tool => {
      // Add basic tool name
      suggestions.push(tool.name);
      
      // Add parameter names
      suggestions.push(...Object.keys(tool.inputSchema?.properties || {}));
      
      // Add tool description
      if (tool.description) {
        suggestions.push(tool.description);
      }
      
      // Add tool usage examples with parameter values for tools that have required parameters
      if (tool.inputSchema?.required && tool.inputSchema.required.length > 0) {
        const examples = generateToolExamples(tool);
        suggestions.push(...examples);
      }
    });
    
    return suggestions.filter(Boolean);
  };

  // Generate example usage patterns for tools with required parameters
  const generateToolExamples = (tool) => {
    const examples = [];
    const required = tool.inputSchema?.required || [];
    const properties = tool.inputSchema?.properties || {};
    
    // Common example values for different parameter types
    const exampleValues = {
      'toolset': ['github', 'docker', 'npm', 'python'],
      'repository': ['owner/repo', 'accentureshark/mentor'],
      'owner': ['github', 'microsoft', 'google'],
      'repo': ['mentor', 'vscode', 'react'],
      'path': ['README.md', 'src/main.js', 'package.json'],
      'query': ['search term', 'example query'],
      'type': ['public', 'private', 'all'],
      'language': ['javascript', 'python', 'java'],
      'file': ['index.js', 'app.py', 'Main.java'],
      'branch': ['main', 'develop', 'feature/branch'],
      'tag': ['v1.0.0', 'latest'],
      'issue_number': ['1', '42'],
      'pull_number': ['1', '10']
    };
    
    // Special case for enable_toolset - create natural language examples
    if (tool.name === 'enable_toolset' && required.includes('toolset')) {
      exampleValues.toolset.forEach(toolset => {
        examples.push(`enable toolset ${toolset}`);
      });
      return examples;
    }
    
    // For other tools, generate examples by combining tool name with parameter examples
    if (required.length > 0) {
      // Get the first required parameter and create examples
      const firstRequired = required[0];
      const paramExamples = exampleValues[firstRequired] || ['example'];
      
      paramExamples.slice(0, 2).forEach(value => { // Limit to 2 examples per tool
        examples.push(`${tool.name} ${firstRequired}:${value}`);
      });
    }
    
    return examples;
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

  // Filter suggestions based on current input
  const updateSuggestions = (inputValue, cursorPos) => {
    const { word, start } = getCurrentWord(inputValue, cursorPos);
    
    if (word.length < 1) {
      setShowSuggestions(false);
      setFilteredSuggestions([]);
      setAutocompletePrefix('');
      return;
    }

    const suggestions = getToolSuggestions();
    const filtered = suggestions.filter(suggestion => 
      suggestion.toLowerCase().includes(word.toLowerCase())
    ).slice(0, 10); // Limit to 10 suggestions

    if (filtered.length > 0) {
      setFilteredSuggestions(filtered);
      setShowSuggestions(true);
      setSelectedIndex(-1);
      setAutocompletePrefix(word);
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

  // Insert selected suggestion
  const insertSuggestion = (suggestion) => {
    const { start, end } = getCurrentWord(value, cursorPosition);
    const newValue = value.slice(0, start) + suggestion + value.slice(end);
    const newCursorPos = start + suggestion.length;
    
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
            <span>Tools suggestions</span>
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
                  <span className="autocomplete-item-text">{suggestion}</span>
                  {/* Highlight matching part and show additional info */}
                  <span className="autocomplete-item-match">
                    {autocompletePrefix && suggestion.toLowerCase().includes(autocompletePrefix.toLowerCase()) && (
                      <small>matches: {autocompletePrefix}</small>
                    )}
                    {/* Show if this is an example with parameters */}
                    {(suggestion.includes(' ') && (suggestion.includes('enable toolset') || suggestion.includes(':'))) && (
                      <small className="autocomplete-example-hint">💡 example usage</small>
                    )}
                  </span>
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