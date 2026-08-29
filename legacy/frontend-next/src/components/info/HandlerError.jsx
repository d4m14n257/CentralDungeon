import { Children } from "react";

import Typography from '@mui/material/Typography';

import { global } from "@/styles/global";

export const ErrorMessage = (props) => {
    return (
        <Typography variant='h3' sx={global.warnError}>
            { props.status ? 
                `Hubo un error en la peticion: ${props.status}` : 
                `Error desconocido...`
            }
            .
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