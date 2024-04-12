import { createContext, useEffect, useState } from "react";

export const User = createContext({id: '', username: '', rol: '', roles: [], handleUpdateRol: () => {}});

export const UserContext = (props) => {
    const { children } = props;
    const [rol, setRol] = useState();

    useEffect(() => {
        const currentRol = sessionStorage.getItem('rol');

        if(currentRol)
            setRol(currentRol)
        else
            setRol('Jugador')
    }, [])

    useEffect(() => {
        sessionStorage.setItem('rol', rol);
    }, [rol])

          
    const handleUpdateRol = (currentRol) => {
        setRol(currentRol);
    }

    return (
        <User.Provider value={{id: '1', username: 'Teshynil', rol: rol, roles: ['Jugador', 'Master', 'Admin'], handleUpdateRol: handleUpdateRol}}>
            {children}
        </User.Provider>
    );
}