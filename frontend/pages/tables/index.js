import { useContext, useState } from "react";

import IconButton from '@mui/material/IconButton';

import AddIcon from '@mui/icons-material/Add';

import TableComponent from "@/components/TableComponent";

import { global } from "@/styles/global";

import { ColorModeContext } from "@/contexts/ColorModeContext";

import CreateModalTable from "@/components/tables/modals/CreateModalTable";
import ActionButtonTable from "@/components/tables/ActionButtonTable";

/* Zona horaria no mostrar 
    Doble click con tabla
*/

const tables = [
    {
        id: 1,
        name: 'Nombre de la mesa',
        master: 'Teshinyl',
        players: 4,
        system: 'Nombre del sistema',
        timezone: 'UTC-02:00',
        schedule: ['Lunes', 'Jueves', 'Viernes'],
    },
    {
        id: 2,
        name: 'Nombre de la mesa',
        master: 'Teshinyl',
        players: 4,
        system: 'Nombre del sistema',
        timezone: 'UTC-02:00',
        schedule: ['Lunes', 'Jueves', 'Viernes'],
    },
    {
        id: 3,
        name: 'Nombre de la mesa',
        master: 'Teshinyl',
        players: 4,
        system: 'Nombre del sistema',
        timezone: 'UTC-02:00',
        schedule: ['Lunes', 'Jueves', 'Viernes'],
    },
    {
        id: 4,
        name: 'Nombre de la mesa',
        master: 'Teshinyl',
        players: 4,
        system: 'Nombre del sistema',
        timezone: 'UTC-02:00',
        schedule: ['Lunes', 'Jueves', 'Viernes'],
    },
    {
        id: 5,
        name: 'Nombre de la mesa',
        master: 'Teshinyl',
        players: 4,
        system: 'Nombre del sistema',
        timezone: 'UTC-02:00',
        schedule: ['Lunes', 'Jueves', 'Viernes'],
    },
    {
        id: 6,
        name: 'Nombre de la mesa',
        master: 'Teshinyl',
        players: 4,
        system: 'Nombre del sistema',
        timezone: 'UTC-02:00',
        schedule: ['Lunes', 'Jueves', 'Viernes'],
    },
]

const columns = [
    {
        id: 'name',
        label: 'Nombre',
    },
    {
        id: 'master',
        label: 'Master',
    },
    {
        id: 'players',
        label: 'Jugadores',
    },
    {
        id: 'timezone',
        label: 'Zona horaria',
    },
    {
        id: 'schedule',
        label: 'Horario',
    },
    {
        id: 'actions',
        label: 'Acciones',
    }
]

export default function Tables () {
    const { mode } = useContext(ColorModeContext);  
    const [ create, setCreate ] = useState(false);

    const handleOpenCreateTable = () => {
        setCreate(true);
    }

    const handleCloseCreateTable = () => {
        setCreate(false);
    }
    
    return (
        <>
            <TableComponent 
                title="Mis mesas"
                columns={columns}
                rows={tables}
                Actions={ActionButtonTable}
            />
            <IconButton 
                sx={{
                    ...global.buttonFloat,
                    backgroundColor: mode == 'dark' ? '#4b4b4b' : '#e8e8e8'
                    }}
                onClick={handleOpenCreateTable}
                >
                <AddIcon fontSize="large"/>
            </IconButton>
            {create && 
                <CreateModalTable 
                    isOpen={create}
                    handleCloseModal={handleCloseCreateTable}
                />
            }
        </>
    );
}