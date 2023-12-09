import Head from 'next/head'

import Toolbar from '@mui/material/Toolbar'
import Container from '@mui/material/Container'

import Header from '@/components/Header';

export default function App({ Component, pageProps }) {
  return (
    <>
        <Head>
            <title>Central Dungeon</title>
        </Head>
        <Header />
        <Toolbar />
        <Container 
          maxWidth='xl'
        >
          <Component {...pageProps} />
        </Container>
    </>
  );
}