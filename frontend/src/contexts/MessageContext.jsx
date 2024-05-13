import { createContext, useState, useReducer } from "react";

import SnackMessage from "@/components/general/SnackMessage";

function reducer (state, action) {
    const { type, value } = action

    switch(type) {
        case 'set-message': {
            return {
                ...state,
                message: value
            }
        }

        case 'set-status': {
            return {
                ...state,
                status: value
            }
        }

        case 'set-info': {
            return {
                ...state,
                info: value
            }
        }
    }
}

export const Message = createContext({ handleOpen: () => {}, setMessage: {}, setStatus: {}, setInfo: {}});

export const MessageContext = (props) => {
    const { children } = props;
    const [open, setOpen] = useState(false);
    const [data, dispatch] = useReducer(reducer, {
        message: '',
        status: '',
        info: ''
    });

    const handleSetMessage = (message) => {
        dispatch({ type: 'set-message', value: message });
    }

    const handleSetStatus = (status) => {
        dispatch({ type: 'set-status', value: status });
    }

    const handleSetInfo = (info) => {
        dispatch({ type: 'set-info', value: info });
    }

    const handleOpen = () => {
        setOpen(true);
    }

    const handleClose = () => {
        setOpen(false)
    }

    return (
        <>
            <Message.Provider value={{ handleOpen, setMessage: handleSetMessage, setStatus: handleSetStatus, setInfo: handleSetInfo }}>
                {children}
            </Message.Provider>
            <SnackMessage 
                message={data.message}
                status={data.status}
                info={data.info}
                open={open}
                onClose={handleClose}
            />
        </>
    );
}