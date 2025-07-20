
import { Checkbox } from 'primereact/checkbox';        

export const CustomCheckbox = ({onChange, checked, className, label}) => {
  return (
    <div className="checkbox-container">
      <Checkbox inputId="checkbox" onChange={onChange} checked={checked} className={className} />
      <label htmlFor="checkbox" className="checkbox-label">
        {label}
      </label>
    </div>
  )
}
