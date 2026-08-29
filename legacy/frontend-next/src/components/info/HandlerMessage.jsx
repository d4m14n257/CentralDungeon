import { Children } from "react";

import Typography from '@mui/material/Typography';

import { global } from "@/styles/global";

export const HandlerMessage = (props) => {
    const { message } = props;

    return (
        <Typography variant="h4" sx={global.warn}>
            { message }.
        </Typography>
    );
}

export function Message(props) {
    let when = null;
    let data = null;

    Children.forEach(props.children, children => {        
        if(children.props.hasData === undefined) {
            data = children;
        }
        else if(!when && children.props.hasData === true){
            when = children;
        }
    })

    return when || data;
}

Message.When = ({ hasData, children }) => hasData && children;
Message.Else = ({ render, children }) => render || children;