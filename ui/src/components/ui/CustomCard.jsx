import { Card } from 'primereact/card';

export const CustomCard = ({ children, title, subTitle, header, footer, className, style }) => {
  return (
    <Card
      title={title}
      subTitle={subTitle}
      header={header}
      footer={footer}
      className={className}
      style={style}
    >
      {children}
    </Card>
  );
};