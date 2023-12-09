import { useState } from 'react';

import ListComponent from '@/components/ListComponent';
import Grid from '@mui/material/Grid'
import TableSelect from '@/components/dashboard/TableSelect';

const tables = {
    public_tables: [
        {
            id: 1,
            name: 'Nombre de mesa 1',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd'
        },
        {
            id: 2,
            name: 'Nombre de mesa 2',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd'
        },
        {
            id: 3,
            name: 'Nombre de mesa 3',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd'
        },
        {
            id: 4,
            name: 'Nombre de mesa 4',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd'
        },
        {
            id: 5,
            name: 'Nombre de mesa 5',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd'
        },
        {
            id: 6,
            name: 'Nombre de mesa 6',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd'
        },

    ],
    first_class_tables: [
        {
            id: 1,
            name: 'Nombre de mesa primera clase 1',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd'
        },
        {
            id: 2,
            name: 'Nombre de mesa primera clase 2',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd'
        },
        {
            id: 3,
            name: 'Nombre de mesa primera clase 3',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd'
        },
        {
            id: 4,
            name: 'Nombre de mesa primera clase 4',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd'
        },
        {
            id: 5,
            name: 'Nombre de mesa primera clase 5',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd'
        },
        {
            id: 6,
            name: 'Nombre de mesa primera clase 6',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd'
        },
    ],
    joined_tables: [
        {
            id: 1,
            name: 'Nombre de mesa unida 1',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd'
        },
        {
            id: 2,
            name: 'Nombre de mesa unida 2',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd'
        },
        {
            id: 3,
            name: 'Nombre de mesa unida 3',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd'
        },
        {
            id: 4,
            name: 'Nombre de mesa unida 4',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd'
        },
        {
            id: 5,
            name: 'Nombre de mesa unida 5',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd'
        },
        {
            id: 6,
            name: 'Nombre de mesa unida 6',
            description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd'
        },
    ]
}

export default function Dashboard () {
    const [tableSelect, setTableSelect] = useState({
        state: false,
        name: '',
        title: ''
    })

    const handleTableSelect = ({name, title}) => {
        if(name) {
            setTableSelect({
                state: true,
                name: name,
                title: title
            })
        }
        else
            setTableSelect({ state: false, name: '', title: ''});
    }

    return (
        <Grid 
            container
            spacing={2}
        >
            {!tableSelect.state ? 
                <>
                    <ListComponent 
                        xs={12}
                        md={8}
                        title={{name: 'Mesas publicas', id: 'public-table'}}
                        tables={tables.public_tables}
                        handleTableSelect={handleTableSelect}
                    />
                    <ListComponent 
                        xs={12}
                        md={4}
                        title={{name: 'Mesas de primera clase', id: 'first-class-table'}}
                        tables={tables.first_class_tables}
                        handleTableSelect={handleTableSelect}
                    />
                    <ListComponent 
                        title={{name: 'Mis mesas', id: 'private-tables'}}
                        tables={tables.joined_tables}
                        handleTableSelect={handleTableSelect}
                    />
                </> : 
                <>
                    <TableSelect 
                        handleTableSelect={handleTableSelect}
                        tableSelect={tableSelect}
                    />
                </>}
        </Grid>
    );

}