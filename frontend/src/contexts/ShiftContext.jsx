import { useEffect, useRef, useState } from "react";

import SnackMessage from "@/components/general/SnackMessage";
export const ShiftContext = (props) => {
    const { children } = props;
    const shifhKey = useRef(false);
    const [open, setOpen] = useState(false);
    const [info, setInfo] = useState(null)

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
                shifhKey.current = true;
                handleSetInfo(shifhKey.current);
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

    const handleSetInfo = (info) => {
        setInfo(info);
    }

    const handleOpen = () => {
        setOpen(true);
    }

    const handleClose = () => {
        setOpen(false)
    }

    return (
        <>
            {children}
            <SnackMessage 
                message='Cuidado, no se solicitara confirmacion para ninguna acción, mientras mantega pulsada la tecla.'
                info={info}
                open={open}
                onClose={handleClose}
            />
        </>
    );
}