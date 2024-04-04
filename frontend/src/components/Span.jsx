import { global } from "@/styles/global";

export default function Span (props) {
    const { title } = props;

    return (
        <span style={global.spanText}>
            {title}
        </span>
    );
}