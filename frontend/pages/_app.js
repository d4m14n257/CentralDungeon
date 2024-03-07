import { useState, useMemo, useContext } from 'react';

import Head from 'next/head';

import { useTheme, ThemeProvider, createTheme } from '@mui/material/styles';

import CssBaseline from '@mui/material/CssBaseline';
import Toolbar from '@mui/material/Toolbar';
import Container from '@mui/material/Container';
import Box from '@mui/material/Box';

import CardSettings from '@/components/CardSettings';
import Header from '@/components/Header';

export default function App({ Component, pageProps }) {
    const [openUser, setOpenUser] = useState(false);
    const [mode, setMode] = useState('dark');
    const colorMode = useMemo(
        () => ({
            toggleColorMode: () => {
                setMode((prevMode) => (prevMode === 'light' ? 'dark' : 'light'));
            },
        }),
        [],
    );

    const theme = useMemo(
        () =>
            createTheme({
                palette: {
                    mode,
                },
            }),
        [mode],
    );

    const handleOpenUser = () => {
        setOpenUser(!openUser);
    }

    return (
        <ThemeProvider theme={darkTheme}>
            <CssBaseline />
            <Head>
                <title>Central Dungeon</title>
            </Head>
            <Header 
                handleOpenUser={handleOpenUser}
            />
            <Toolbar />
            <Box
                sx={{margin: 2.5}}
            >
                <Container 
                    maxWidth='xl'
                >
                    <Component {...pageProps} />
                    {openUser && <CardSettings 
                        handleOpenUser={handleOpenUser}
                        openUser={openUser}
                    />}
                </Container>
            </Box>
        </ThemeProvider>
    );
}