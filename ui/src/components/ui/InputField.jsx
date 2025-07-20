
import { InputText } from 'primereact/inputtext';
        
export const InputField = ({value, onChange, disabled, className, id, placeholder, type = "text"}) => {
  return (
    <InputText
      className={className}
      disabled={disabled}
      value={value}
      onChange={onChange}
      id={id}
      placeholder={placeholder}
      type={type}
    />
  )
}
