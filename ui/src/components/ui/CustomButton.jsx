
import { Button } from 'primereact/button';

export const CustomButton = ({className, label, onClick, disabled, icon, type, tooltip, style, ariaLabel}) => {
  return (
    <Button
        className={className}
        label={label}
        onClick={onClick}
        disabled={disabled}
        icon={icon}
        type={type}
        tooltip={tooltip}
        style={style}
        aria-label={ariaLabel}
    />
  )
}
