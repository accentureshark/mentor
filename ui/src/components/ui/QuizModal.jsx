import { Dialog } from "primereact/dialog";
import { Button } from "primereact/button";
import { InputField } from "./InputField";
import { useState, useEffect } from "react";

export const QuizModal = ({ visible, onHide, onSave, editMode = false, initialData = null }) => {
    const [quizTitle, setQuizTitle] = useState("");
    const [quizPrompt, setQuizPrompt] = useState("");
    const [defaultPrompt, setDefaultPrompt] = useState("");
    const [questions, setQuestions] = useState([{ id: 1, value: "", options: [""], random: false }]);
    const MAX_QUESTIONS = 5;

    // Fetch default prompt on component mount
    useEffect(() => {
        const fetchDefaultPrompt = async () => {
            try {
                const baseUrl = import.meta.env.VITE_API_URL || '/api';
                const response = await fetch(`${baseUrl}/quiz/prompt/default`);
                const prompt = await response.text();
                setDefaultPrompt(prompt);
            } catch (error) {
                console.error('Error fetching default prompt:', error);
            }
        };
        fetchDefaultPrompt();
    }, []);

    // Initialize form when modal opens or editMode/initialData changes
    useEffect(() => {
        if (editMode && initialData) {
            setQuizTitle(initialData.tema || "");
            setQuizPrompt(initialData.prompt || "");

            if (initialData.steps && initialData.steps.length > 0) {
                const formattedQuestions = initialData.steps.map((step, index) => ({
                    id: parseInt(step.id) || index + 1,
                    value: step.texto || "",
                    options: step.opciones && step.opciones.length > 0 ? step.opciones : [""],
                    random: step.random || false
                }));
                setQuestions(formattedQuestions);
            }
        } else if (!editMode) {
            // Reset form for create mode
            setQuizTitle("");
            setQuizPrompt("");
            setQuestions([{ id: 1, value: "", options: [""], random: false }]);
        }
    }, [editMode, initialData, visible]);

    const addQuestion = () => {
        if (questions.length < MAX_QUESTIONS) {
            const newId = Math.max(...questions.map(q => q.id)) + 1;
            setQuestions(prev => [...prev, { id: newId, value: "", options: [""], random: false }]);
        }
    };

    const removeQuestion = (id) => {
        if (questions.length > 1) {
            setQuestions(prev => prev.filter(question => question.id !== id));
        }
    };

    const updateQuestion = (id, value) => {
        setQuestions(prev => 
            prev.map(question => 
                question.id === id ? { ...question, value } : question
            )
        );
    };

    const updateQuestionRandom = (id, random) => {
        setQuestions(prev => 
            prev.map(question => 
                question.id === id ? { ...question, random } : question
            )
        );
    };

    const addOption = (questionId) => {
        setQuestions(prev => 
            prev.map(question => 
                question.id === questionId 
                    ? { ...question, options: [...question.options, ""] }
                    : question
            )
        );
    };

    const removeOption = (questionId, optionIndex) => {
        setQuestions(prev => 
            prev.map(question => 
                question.id === questionId 
                    ? { ...question, options: question.options.filter((_, index) => index !== optionIndex) }
                    : question
            )
        );
    };

    const updateOption = (questionId, optionIndex, value) => {
        setQuestions(prev => 
            prev.map(question => 
                question.id === questionId 
                    ? { 
                        ...question, 
                        options: question.options.map((option, index) => 
                            index === optionIndex ? value : option
                        )
                    }
                    : question
            )
        );
    };

    const handleClose = () => {
        if (!editMode) {
            // Only reset form when creating new quiz
            setQuizTitle("");
            setQuizPrompt("");
            setQuestions([{ id: 1, value: "", options: [""], random: false }]);
        }
        onHide();
    };

    const handleSave = () => {
        const quizData = {
            tema: quizTitle,
            prompt: quizPrompt,
            steps: questions
                .filter(q => q.value.trim())
                .map((q, idx) => ({
                    step: idx + 1,
                    id: String(q.id),
                    texto: q.value,
                    opciones: q.options.filter(opt => opt.trim()),
                    random: q.random || false
                }))
        };
        
        if (editMode && initialData) {
            // Include documentId for editing
            quizData.documentId = initialData.documentId;
        }
        
        onSave(quizData);
        handleClose();
    };

    return (
        <Dialog
            header={`${editMode ? 'Edit' : 'Create'} Quiz ${quizTitle ? `- ${quizTitle}` : ''}`}
            visible={visible} 
            onHide={handleClose} 
            modal 
            draggable={false} 
            className="dialog quiz-modal"
            style={{ width: '50vw', minWidth: '400px', height: '700px' }}
        >
            <div className="quiz-content">
                {/* Quiz Title */}
                <div className="quiz-title-section">
                    <label htmlFor="quiz-title" className="quiz-title-label">
                        <i className="pi pi-bookmark" style={{ marginRight: '0.5rem' }}></i>
                        Quiz Title
                    </label>
                    <InputField
                        id="quiz-title"
                        value={quizTitle}
                        onChange={(e) => setQuizTitle(e.target.value)}
                        placeholder="Enter the title of your quiz..."
                        className="quiz-title-input"
                    />
                </div>

                {/* Quiz Prompt */}
                <div className="quiz-prompt-section">
                    <label htmlFor="quiz-prompt" className="quiz-prompt-label">
                        <i className="pi pi-comments" style={{ marginRight: '0.5rem' }}></i>
                        LLM Prompt (optional)
                    </label>
                    <textarea
                        id="quiz-prompt"
                        value={quizPrompt}
                        onChange={(e) => setQuizPrompt(e.target.value)}
                        placeholder={defaultPrompt || "Enter the prompt that will be used to evaluate the answers with the LLM..."}
                        className="quiz-prompt-input"
                        rows="3"
                    />
                </div>
                {questions.map((question, index) => (
                    <div key={question.id} className="question-item">
                        <div className="question-header">
                            <p className="question-label">Question number {index + 1}</p>
                            {questions.length > 1 && (
                                <Button 
                                    icon="pi pi-trash" 
                                    className="p-button-rounded p-button-text p-button-danger p-button-sm"
                                    onClick={() => removeQuestion(question.id)}
                                    tooltip="Remove question"
                                    tooltipOptions={{ position: 'top' }}
                                />
                            )}
                        </div>
                        <InputField 
                            value={question.value}
                            onChange={(e) => updateQuestion(question.id, e.target.value)}
                            placeholder={`Enter question ${index + 1}...`}
                            className="question-input"
                        />
                        
                        {/* Random flag section */}
                        <div className="question-random-section">
                            <label className="random-checkbox-label">
                                <input
                                    type="checkbox"
                                    checked={question.random || false}
                                    onChange={(e) => updateQuestionRandom(question.id, e.target.checked)}
                                    className="random-checkbox"
                                />
                                <i className="pi pi-refresh" style={{ marginLeft: '0.5rem', marginRight: '0.5rem' }}></i>
                                Shuffle options randomly
                            </label>
                        </div>
                        
                        {/* Options section */}
                        <div className="question-options">
                            <div className="options-header">
                                    <label className="options-label">
                                    <i className="pi pi-list" style={{ marginRight: '0.5rem' }}></i>
                                    Answer options
                                </label>
                                <Button
                                    icon="pi pi-plus"
                                    className="p-button-rounded p-button-text p-button-sm"
                                    onClick={() => addOption(question.id)}
                                    tooltip="Add option"
                                    tooltipOptions={{ position: 'top' }}
                                />
                            </div>
                            {question.options.map((option, optionIndex) => (
                                <div key={optionIndex} className="option-item">
                                    <InputField
                                        value={option}
                                        onChange={(e) => updateOption(question.id, optionIndex, e.target.value)}
                                        placeholder={`Option ${optionIndex + 1}...`}
                                        className="option-input"
                                    />
                                    {question.options.length > 1 && (
                                        <Button
                                            icon="pi pi-times"
                                            className="p-button-rounded p-button-text p-button-danger p-button-sm"
                                            onClick={() => removeOption(question.id, optionIndex)}
                                            tooltip="Remove option"
                                            tooltipOptions={{ position: 'top' }}
                                        />
                                    )}
                                </div>
                            ))}
                        </div>
                    </div>
                ))}
                
                <div className="add-question-section">
                    <Button
                        icon="pi pi-plus"
                        label={`Add Question ${questions.length < MAX_QUESTIONS ? `(${MAX_QUESTIONS - questions.length} remaining)` : ''}`}
                        className="p-button-outlined"
                        onClick={addQuestion}
                        disabled={questions.length >= MAX_QUESTIONS}
                    />
                    {questions.length >= MAX_QUESTIONS && (
                        <small className="max-questions-warning">
                            <i className="pi pi-info-circle" style={{ marginRight: '0.25rem' }}></i>
                            Maximum {MAX_QUESTIONS} questions allowed
                        </small>
                    )}
                </div>

                <div className="modal-footer">
                    <Button
                        label="Cancel"
                        icon="pi pi-times"
                        className="p-button-outlined"
                        onClick={handleClose}
                    />
                    <Button
                        label={editMode ? "Update" : "Save"}
                        icon="pi pi-check"
                        className="p-button-primary"
                        severity="success" // Cambiado para mejor visibilidad
                        disabled={!quizTitle.trim() || questions.some(q => !q.value.trim() || q.options.some(opt => !opt.trim()))}
                        onClick={handleSave}
                    />
                </div>
            </div>
        </Dialog>
    );
};