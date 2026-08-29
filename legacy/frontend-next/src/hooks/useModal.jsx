import { useState, useRef } from "react";

export const useModal = () => {
    const dataModal = useRef(null)
    const [open, setOpen] = useState(false)
    
    const handleOpenModal = () => setOpen(true);
    const handleOpenModalWithData = (data) => {
        dataModal.current = data;
        setOpen(true);
    }
    const handleCloseModal = () => setOpen(false);

    return {
        dataModal,
        open,
        setOpen,
        handleOpenModal,
        handleCloseModal,
        handleOpenModalWithData
    }
}