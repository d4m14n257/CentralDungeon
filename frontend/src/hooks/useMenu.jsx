import { useMemo, useState } from "react";

export const useMenu = () => {
    const [open, setOpen] = useState(null)
    const openStatus = useMemo(() => {
        return Boolean(open)
    }, [open])
    
    const handleOpenMenu = (event) => {
        setOpen(event.currentTarget)
    };
    const handleCloseMenu = () => setOpen(null);

    return {
        open,
        openStatus,
        handleOpenMenu,
        handleCloseMenu
    }
}