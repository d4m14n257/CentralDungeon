export default function Span (props) {
    const { title } = props;

    return (
        <span style={{fontWeight: 'bold'}}>
            {title}
        </span>
    );
}