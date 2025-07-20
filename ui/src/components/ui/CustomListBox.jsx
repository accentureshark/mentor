import { ListBox } from 'primereact/listbox';
    
export const CustomListBox = ({value, onChange, options, optionLabel, placeholder, className}) => {
  return (
    <ListBox 
        value={value}
        onChange={onChange}
        options={options}
        optionLabel={optionLabel}
        placeholder={placeholder} 
        className={className}
    />
  )
}
