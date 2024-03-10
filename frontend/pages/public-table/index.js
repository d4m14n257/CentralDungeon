import Grid from '@mui/material/Grid';

import ListComponent from '@/components/ListComponent';

const public_tables = [
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

]

export default function PublicTable () {
    return (
        <Grid
            container
            spacing={2}
        >
            <ListComponent 
                info={{name: 'Mesas publicas', id: 'public-table'}}
                tables={public_tables}
            />
        </Grid> 
    );
}