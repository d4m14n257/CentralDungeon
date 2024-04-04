import Head from 'next/head';

import { useState, useMemo, useEffect } from 'react';

import { ThemeProvider, createTheme } from '@mui/material/styles';
import useMediaQuery from '@mui/material/useMediaQuery';

import CssBaseline from '@mui/material/CssBaseline';
import Toolbar from '@mui/material/Toolbar';
import Container from '@mui/material/Container';
import Box from '@mui/material/Box';

import CardSettings from '@/components/general/CardSettings';
import Header from '@/components/general/Header';

import { ColorModeContext } from '@/contexts/ColorModeContext';
import { UserContext } from '@/contexts/UserContext';

export default function App({ Component, pageProps }) {
    const darkModeMediaQuery = useMediaQuery('(prefers-color-scheme: dark)');

    const [openUser, setOpenUser] = useState(false);
    const [mode, setMode] = useState();
    const [rol, setRol] = useState();

    useEffect(() => {
        const currentTheme = localStorage.getItem('theme');
        const currentRol = sessionStorage.getItem('rol');
        
        if(currentTheme) 
            setMode(currentTheme)
        else
            setMode(darkModeMediaQuery ? 'dark' : 'light');

        if(currentRol)
            setRol(currentRol)
        else
            setRol('Jugador')

    }, [])

    useEffect(() => {
        localStorage.setItem('theme', mode);
    }, [mode])

    useEffect(() => {
        sessionStorage.setItem('rol', rol);
    }, [rol])

    const handleUpdateRol = (currentRol) => {
        setRol(currentRol);
    }

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

    const handleOpenUser = () => {
        setOpenUser(!openUser);
    }

    return (
        <UserContext.Provider value={{username: 'Teshynil', rol: rol, roles: ['Jugador', 'Master', 'Admin'], handleUpdateRol: handleUpdateRol}}>
            <ColorModeContext.Provider value={{ colorMode, mode }}>
                <ThemeProvider theme={theme}>
                    <CssBaseline />
                    <Head>
                        <title>Central Dungeon</title>
                    </Head>
                    <Header
                        handleOpenUser={handleOpenUser}
                    />
                    <Toolbar />
                    <Box
                        sx={{ margin: 2.5 }}
                    >
                        <Container
                            maxWidth='xl'
                        >
                            <Component {...pageProps} />
                            {openUser &&
                                <CardSettings
                                    handleOpenUser={handleOpenUser}
                                    openUser={openUser}
                                />
                            }
                        </Container>
                    </Box>
                </ThemeProvider>
            </ColorModeContext.Provider>
        </UserContext.Provider>
    );
}