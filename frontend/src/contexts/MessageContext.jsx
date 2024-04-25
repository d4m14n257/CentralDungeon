import { createContext, useState, useReducer, useEffect, useRef } from "react";

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
    const shifhKey = useRef(false);
    const [open, setOpen] = useState(false);
    const [data, dispatch] = useReducer(reducer, {
        message: '',
        status: '',
        info: ''
    });

    useEffect(() => {
        document.addEventListener('keydown', handleKeyDownDetected);
        document.addEventListener('keyup', handleKeyUpDetected)

        return () => {
            document.removeEventListener('keydown', handleKeyDownDetected);
            document.removeEventListener('keyup', handleKeyUpDetected);
          };
    }, [])
    
    const handleKeyDownDetected = (event) => {
        if(event.key == 'Shift') {
            if(!shifhKey.current) {
                handleSetMessage('Cuidado, no se solicitara confirmacion para ninguna acción, mientras mantega pulsada la tecla.')
                shifhKey.current = true;
                handleSetInfo(shifhKey.current);
                handleSetStatus('')
                handleOpen();
            }
        }
    }

    const handleKeyUpDetected = (event) => {
        if(event.key == 'Shift') {
            if(shifhKey) {
                shifhKey.current = false;
            }
        }
    }

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
        handleSetStatus('')
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