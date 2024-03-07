import { useEffect, useState } from 'react';

import ListComponent from '@/components/ListComponent';
import Grid from '@mui/material/Grid';
import IconButton from '@mui/material/IconButton';

import ArrowBackIcon from '@mui/icons-material/ArrowBack';

/*
    En vez de cambioos de estados para ver las mesas, sean ahora diferente.
*/

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
        name: '',
        id: ''
    })

    const [select, setSelect] = useState(null);

    const handleTableSelect = ({id, name}) => {
        if(id) {
            if(id === 'public-table')
                setSelect(tables.public_tables);
            else if (id === 'first-class-table')
                setSelect(tables.first_class_tables);
            else if (id === 'joined-table')
                setSelect(joined_tables);

            setTableSelect({
                name: name,
                id: id
            });
        }
        else{
            setSelect(null);
            setTableSelect({ name: '', id: ''});
        }
    }

    return (
        <Grid 
            container
            spacing={2}
        >
            {!select ? 
                <>
                    <ListComponent 
                        xs={12}
                        md={8}
                        info={{name: 'Mesas publicas', id: 'public-table'}}
                        tables={tables.public_tables}
                        handleTableSelect={handleTableSelect}
                        isSelect={true}
                    />
                    <ListComponent 
                        xs={12}
                        md={4}
                        info={{name: 'Mesas de primera clase', id: 'first-class-table'}}
                        tables={tables.first_class_tables}
                        handleTableSelect={handleTableSelect}
                        isSelect={true}
                    />
                    <ListComponent 
                        info={{name: 'Mis mesas', id: 'joined-table'}}
                        tables={tables.joined_tables}
                        handleTableSelect={handleTableSelect}
                        isSelect={true}
                    />
                </> : 
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
                </>}
        </Grid>
    );

}