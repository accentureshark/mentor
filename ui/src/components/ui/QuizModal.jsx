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
            header={`${editMode ? 'Editar' : 'Crear'} Quiz ${quizTitle ? `- ${quizTitle}` : ''}`}
            visible={visible} 
            onHide={handleClose} 
            modal 
            draggable={false} 
            className="dialog quiz-modal"
            style={{ width: '50vw', minWidth: '400px', height: '700px' }}
        >
            <div className="quiz-content">
                {/* Título del Quiz */}
                <div className="quiz-title-section">
                    <label htmlFor="quiz-title" className="quiz-title-label">
                        <i className="pi pi-bookmark" style={{ marginRight: '0.5rem' }}></i>
                        Título del Quiz
                    </label>
                    <InputField
                        id="quiz-title"
                        value={quizTitle}
                        onChange={(e) => setQuizTitle(e.target.value)}
                        placeholder="Ingresa el título de tu quiz..."
                        className="quiz-title-input"
                    />
                </div>

                {/* Prompt del Quiz */}
                <div className="quiz-prompt-section">
                    <label htmlFor="quiz-prompt" className="quiz-prompt-label">
                        <i className="pi pi-comments" style={{ marginRight: '0.5rem' }}></i>
                        Prompt para LLM (opcional)
                    </label>
                    <textarea
                        id="quiz-prompt"
                        value={quizPrompt}
                        onChange={(e) => setQuizPrompt(e.target.value)}
                        placeholder={defaultPrompt || "Ingresa el prompt que se usará para evaluar las respuestas con el LLM..."}
                        className="quiz-prompt-input"
                        rows="3"
                    />
                </div>
                {questions.map((question, index) => (
                    <div key={question.id} className="question-item">
                        <div className="question-header">
                            <p className="question-label">Pregunta número {index + 1}</p>
                            {questions.length > 1 && (
                                <Button 
                                    icon="pi pi-trash" 
                                    className="p-button-rounded p-button-text p-button-danger p-button-sm"
                                    onClick={() => removeQuestion(question.id)}
                                    tooltip="Eliminar pregunta"
                                    tooltipOptions={{ position: 'top' }}
                                />
                            )}
                        </div>
                        <InputField 
                            value={question.value}
                            onChange={(e) => updateQuestion(question.id, e.target.value)}
                            placeholder={`Ingresa la pregunta ${index + 1}...`}
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
                                Ordenar opciones aleatoriamente
                            </label>
                        </div>
                        
                        {/* Options section */}
                        <div className="question-options">
                            <div className="options-header">
                                <label className="options-label">
                                    <i className="pi pi-list" style={{ marginRight: '0.5rem' }}></i>
                                    Opciones de respuesta
                                </label>
                                <Button
                                    icon="pi pi-plus"
                                    className="p-button-rounded p-button-text p-button-sm"
                                    onClick={() => addOption(question.id)}
                                    tooltip="Agregar opción"
                                    tooltipOptions={{ position: 'top' }}
                                />
                            </div>
                            {question.options.map((option, optionIndex) => (
                                <div key={optionIndex} className="option-item">
                                    <InputField
                                        value={option}
                                        onChange={(e) => updateOption(question.id, optionIndex, e.target.value)}
                                        placeholder={`Opción ${optionIndex + 1}...`}
                                        className="option-input"
                                    />
                                    {question.options.length > 1 && (
                                        <Button
                                            icon="pi pi-times"
                                            className="p-button-rounded p-button-text p-button-danger p-button-sm"
                                            onClick={() => removeOption(question.id, optionIndex)}
                                            tooltip="Eliminar opción"
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
                        label={`Agregar Pregunta ${questions.length < MAX_QUESTIONS ? `(${MAX_QUESTIONS - questions.length} restantes)` : ''}`}
                        className="p-button-outlined"
                        onClick={addQuestion}
                        disabled={questions.length >= MAX_QUESTIONS}
                    />
                    {questions.length >= MAX_QUESTIONS && (
                        <small className="max-questions-warning">
                            <i className="pi pi-info-circle" style={{ marginRight: '0.25rem' }}></i>
                            Máximo {MAX_QUESTIONS} preguntas permitidas
                        </small>
                    )}
                </div>

                <div className="modal-footer">
                    <Button
                        label="Cancelar"
                        icon="pi pi-times"
                        className="p-button-outlined"
                        onClick={handleClose}
                    />
                    <Button
                        label={editMode ? "Actualizar" : "Guardar"}
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