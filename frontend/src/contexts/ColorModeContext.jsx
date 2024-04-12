import { createContext, useMemo, useState, useEffect } from "react";

import { ThemeProvider, createTheme } from '@mui/material/styles';
import useMediaQuery from '@mui/material/useMediaQuery';

export const ColorMode = createContext({ toggleColorMode: () => { }, mode: " "});

export const ColorModeContext = (props) => {
    const { children } = props;
    const darkModeMediaQuery = useMediaQuery('(prefers-color-scheme: dark)');

    const [mode, setMode] = useState();

    useEffect(() => {
        const currentTheme = localStorage.getItem('theme');
        
        if(currentTheme) 
            setMode(currentTheme)
        else
            setMode(darkModeMediaQuery ? 'dark' : 'light');

    }, [])

    useEffect(() => {
        localStorage.setItem('theme', mode);
    }, [mode])

    const colorMode = useMemo(
        () => ({
            toggleColorMode: () => {
                setMode((prevMode) => (prevMode === "light" ? "dark" : "light"));
            },
        }), []
    );

    const theme = useMemo(
        () =>
            createTheme({
                palette: {
                    mode,
                },
            }),
        [mode]
    );

    return (
        <ColorMode.Provider value={{ colorMode, mode }}>
            <ThemeProvider theme={theme}>
                {children}
            </ThemeProvider>
        </ColorMode.Provider>
    );

}