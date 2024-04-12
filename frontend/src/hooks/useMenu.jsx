import { useState } from "react";

export const useMenu = () => {
    const [open, setOpen] = useState(false)
    
    const handleOpenMenu = () => setOpen(true);
    const handleCloseMenu = () => setOpen(false);

    return {
        open,
        setOpen,
        handleOpenMenu,
        handleCloseMenu
    }
}