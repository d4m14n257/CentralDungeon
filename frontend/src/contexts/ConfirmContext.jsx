import { createContext, useState, useCallback } from "react";

import DialogConfirmed from "@/components/general/DialogConfirmed";

export const Confirm = createContext({confirm: () => {}, setMessage: () => {}});

export const ConfirmContext = (props) => {
    const { children } = props;
    const [message, setMessage] = useState('');
    const [resolveReject, setResolveReject] = useState([]);
    const [resolve, reject] = resolveReject;

    const confirm = useCallback(() => {
        return new Promise((resolve, reject) => {
            setResolveReject([resolve, reject]);
        });
    }, []);

    const handleClose = useCallback(() => {
        setResolveReject([]);
    }, []);
        
    const handleCancel = useCallback(() => {
        if (reject) {
            reject();
            handleClose();
        }
    }, [reject, handleClose]);
    
    const handleConfirm = useCallback(() => {
        if (resolve) {
            resolve();
            handleClose();
        }
    }, [resolve, handleClose]);

    return (
        <>
            <Confirm.Provider value={{ confirm, setMessage }}>
                {children}
            </Confirm.Provider>
            <DialogConfirmed
                open={resolveReject.length === 2}
                message={message}
                onClose={handleCancel}
                onCancel={handleCancel}
                onConfirm={handleConfirm}
            />
        </>
    );
}