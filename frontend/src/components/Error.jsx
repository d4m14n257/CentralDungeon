import { Children } from "react";

import Typography from '@mui/material/Typography';

const ErrorMessage = (props) => {
    const { err } = props;

    return (
        <Typography>
            Hubo un error en la peticion: { err.status }.
        </Typography>
    );
}

export function Error(props) {
    let normal = null;
    let error = null;

    Children.forEach(props.children, children => {
        if(children.props.err)
            error = children;
        else
            normal = children;
    })

    return error ? <ErrorMessage err={error.props.err} /> : normal;
}

Error.When = ({ children }) => children
Error.Else = ({ err }) => err