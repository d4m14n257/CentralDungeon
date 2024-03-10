import Grid from '@mui/material/Grid';

import ListComponent from '@/components/ListComponent';

const joined_tables = [
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

export default function JoinedTable () {
    return (
        <Grid
            container
            spacing={2}
        >
            <ListComponent 
                info={{name: 'Mis mesas', id: 'joined-table'}}
                tables={joined_tables}
            />
        </Grid> 
    );
}