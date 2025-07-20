
import { InputTextarea } from 'primereact/inputtextarea';

export const TextareaField = ({
  name,
  placeholder,
  value,
  onChange,
  rows,
  autoResize = false,
  onKeyDown,
  className = '',
  disabled = false
}) => {
  return (
    <InputTextarea
      className={className}
        name={name}
        placeholder={placeholder}
        value={value}
        onChange={onChange}
        rows={rows}
        autoResize={autoResize}
        onKeyDown={onKeyDown}
        disabled={disabled}
    />
  )
}
