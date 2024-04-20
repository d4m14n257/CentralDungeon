import { Children } from "react";

import Typography from '@mui/material/Typography';

import { global } from "@/styles/global";

export const ErrorMessage = (props) => {
    const { err } = props;

    return (
        <Typography variant='h3' sx={global.warnError}>
            Hubo un error en la peticion: { err }.
        </Typography>
    );
}

export function Error(props) {
    let when = null;
    let error = null;

    Children.forEach(props.children, children => {
        if(children.props.isError === undefined) {
            error = children;
        }
        else if(!when && children.props.isError === false){
            when = children;
        }
    })

    return when || error;
}

Error.When = ({ isError, children }) => !isError && children;
Error.Else = ({ render, children }) => render || children;