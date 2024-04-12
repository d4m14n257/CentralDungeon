import Head from 'next/head';

import { useState } from 'react';

import CssBaseline from '@mui/material/CssBaseline';
import Toolbar from '@mui/material/Toolbar';
import Container from '@mui/material/Container';
import Box from '@mui/material/Box';

import CardSettings from '@/components/general/CardSettings';
import Header from '@/components/general/Header';

import { ColorModeContext } from '@/contexts/ColorModeContext';
import { UserContext } from '@/contexts/UserContext';

//TODO: Add loading effects on pages.

export default function App({ Component, pageProps }) {
    const [openUser, setOpenUser] = useState(false);

    const handleOpenUser = () => {
        setOpenUser(!openUser);
    }

    return (
        <UserContext>
            <ColorModeContext>
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
            </ColorModeContext>
        </UserContext>
    );
}