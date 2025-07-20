import { useState, useEffect } from 'react';

export const ProcessingTimer = ({ isActive, className = '' }) => {
    const [elapsedTime, setElapsedTime] = useState(0);

    useEffect(() => {
        let interval = null;
        
        if (isActive) {
            setElapsedTime(0);
            interval = setInterval(() => {
                setElapsedTime(prevTime => prevTime + 1);
            }, 1000);
        } else {
            setElapsedTime(0);
        }

        return () => {
            if (interval) {
                clearInterval(interval);
            }
        };
    }, [isActive]);

    const formatTime = (seconds) => {
        const mins = Math.floor(seconds / 60);
        const secs = seconds % 60;
        return `${mins}:${secs.toString().padStart(2, '0')}`;
    };

    if (!isActive) {
        return null;
    }

    return (
        <div className={`processing-timer ${className}`} style={{
            display: 'flex',
            alignItems: 'center',
            gap: '0.5rem',
            padding: '0.5rem',
            backgroundColor: '#f8f9fa',
            borderRadius: '4px',
            border: '1px solid #e9ecef',
            fontSize: '0.9rem',
            color: '#64748b'
        }}>
            <i className="pi pi-clock" />
            <span>Tiempo transcurrido: {formatTime(elapsedTime)}</span>
        </div>
    );
};