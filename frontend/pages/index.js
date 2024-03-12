import { useRouter } from 'next/router';

import Grid from '@mui/material/Grid';

import ListComponent from '@/components/ListComponent';
/*
    En vez de cambioos de estados para ver las mesas, sean ahora diferente.
*/

const tables = {
    public_tables: [
        {
            id: 1,
            name: 'Nombre de mesa 1',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd',
            startdate: '2023-12-21T19:30:00',
            timezone: 'UTC-02:00'
        },
        {
            id: 2,
            name: 'Nombre de mesa 2',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd',
            startdate: '2023-12-21T19:30:00',
            timezone: 'UTC-02:00'
        },
        {
            id: 3,
            name: 'Nombre de mesa 3',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd',
            startdate: '2023-12-21T19:30:00',
            timezone: 'UTC-02:00'
        },
        {
            id: 4,
            name: 'Nombre de mesa 4',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd',
            startdate: '2023-12-21T19:30:00',
            timezone: 'UTC-02:00'
        },
        {
            id: 5,
            name: 'Nombre de mesa 5',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd',
            startdate: '2023-12-21T19:30:00',
            timezone: 'UTC-02:00'
        },
        {
            id: 6,
            name: 'Nombre de mesa 6',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd',
            startdate: '2023-12-21T19:30:00',
            timezone: 'UTC-02:00'
        },

    ],
    first_class_tables: [
        {
            id: 1,
            name: 'Nombre de mesa primera clase 1',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd',
            startdate: '2023-12-21T19:30:00',
            timezone: 'UTC-02:00'
        },
        {
            id: 2,
            name: 'Nombre de mesa primera clase 2',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd',
            startdate: '2023-12-21T19:30:00',
            timezone: 'UTC-02:00'
        },
        {
            id: 3,
            name: 'Nombre de mesa primera clase 3',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd',
            startdate: '2023-12-21T19:30:00',
            timezone: 'UTC-02:00'
        },
        {
            id: 4,
            name: 'Nombre de mesa primera clase 4',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd',
            startdate: '2023-12-21T19:30:00',
            timezone: 'UTC-02:00'
        },
        {
            id: 5,
            name: 'Nombre de mesa primera clase 5',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd',
            startdate: '2023-12-21T19:30:00',
            timezone: 'UTC-02:00'
        },
        {
            id: 6,
            name: 'Nombre de mesa primera clase 6',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd',
            startdate: '2023-12-21T19:30:00',
            timezone: 'UTC-02:00'
        },
    ],
    joined_tables: [
        {
            id: 1,
            name: 'Nombre de mesa unida 1',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd',
            startdate: '2023-12-21T19:30:00',
            timezone: 'UTC-02:00'
        },
        {
            id: 2,
            name: 'Nombre de mesa unida 2',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd',
            startdate: '2023-12-21T19:30:00',
            timezone: 'UTC-02:00'
        },
        {
            id: 3,
            name: 'Nombre de mesa unida 3',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd',
            startdate: '2023-12-21T19:30:00',
            timezone: 'UTC-02:00'
        },
        {
            id: 4,
            name: 'Nombre de mesa unida 4',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd',
            startdate: '2023-12-21T19:30:00',
            timezone: 'UTC-02:00'
        },
        {
            id: 5,
            name: 'Nombre de mesa unida 5',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd',
            startdate: '2023-12-21T19:30:00',
            timezone: 'UTC-02:00'
        },
        {
            id: 6,
            name: 'Nombre de mesa unida 6',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd',
            startdate: '2023-12-21T19:30:00',
            timezone: 'UTC-02:00'
        },
    ]
}

export default function Dashboard () {
    const router = useRouter();

    const handleTableSelect = ({id}) => {
        if(id) {
            if(id === 'public-table')
                router.push(`/public-table`)
            else if (id === 'first-class-table')
                router.push(`/first-class-table`)
            else if (id === 'joined-table')
                router.push(`/joined-table`)
        }
    }

    return (
        <Grid 
            container
            spacing={2}
        >

            <ListComponent 
                xs={12}
                lg={8}
                info={{name: 'Mesas publicas', id: 'public-table'}}
                tables={tables.public_tables}
                handleTableSelect={handleTableSelect}
                isSelect={true}
            />
            <ListComponent 
                xs={12}
                lg={4}
                info={{name: 'Mesas de primera clase', id: 'first-class-table'}}
                tables={tables.first_class_tables}
                handleTableSelect={handleTableSelect}
                isSelect={true}
            />
            <ListComponent 
                info={{name: 'Mesas jugando', id: 'joined-table'}}
                tables={tables.joined_tables}
                handleTableSelect={handleTableSelect}
                isSelect={true}
            />
                {/* </> : 
                <>
                    <IconButton
                        size='large'
                        onClick={() => handleTableSelect({name: '', id: ''})}
                    >
                        <ArrowBackIcon fontSize='inherit'/>
                    </IconButton>
                    <ListComponent 
                        info={tableSelect}
                        tables={select}
                        isSelect={false}
                    />
                </>} */}
        </Grid>
    );

}