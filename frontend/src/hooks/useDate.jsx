import { useCallback, useMemo } from "react"

export const useDate = () => {
    const optionsWithTime = useMemo(() => ({
        weekday: 'long', 
        year: 'numeric', 
        month: 'long', 
        day: 'numeric', 
        hour: 'numeric', 
        minute: 'numeric'
    }))

    const optionsWithoutTime = useMemo(() => ({
        weekday: 'long', 
        year: 'numeric', 
        month: 'long', 
        day: 'numeric', 
    }))

    const handleDatetime = useCallback((created) => {
        const createdDate = new Date(created);
        return createdDate.toLocaleDateString('es-ES', optionsWithTime);
    }, [optionsWithTime])

    const handleDate = useCallback((created) => {
        const createdDate = new Date(created);
        return createdDate.toLocaleDateString('es-ES', optionsWithoutTime);
    }, [optionsWithTime])

    return {
        handleDatetime,
        handleDate
    }
}