import { useCallback, useMemo } from "react"

export const useDate = () => {
    const options = useMemo(() => ({
        weekday: 'long', 
        year: 'numeric', 
        month: 'long', 
        day: 'numeric', 
        hour: 'numeric', 
        minute: 'numeric'
    }))

    const handleDate = useCallback((created) => {
        const createdDate = new Date(created);
        return createdDate.toLocaleDateString('es-ES', options);
    }, [options])

    return {
        handleDate
    }
}