import { useContext, useState } from "react";
import { useRouter } from "next/router";

import IconButton from '@mui/material/IconButton';

import AddIcon from '@mui/icons-material/Add';

import TableComponent from "@/components/TableComponent";

import { global } from "@/styles/global";

import { ColorModeContext } from "@/context/ColorModeContext";
import CreateModalTable from "@/components/tables/modals/CreateModalTable";

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
    const router = useRouter();
    const { mode } = useContext(ColorModeContext);  
    const [ create, setCreate ] = useState(false);

    
    const handleTableRoute = (id) => {
        router.push(`/tables/${id}`)
    }

    const handleOpenCreateTable = () => {
        setCreate(true);
    }

    const handleCloseCreateTable = () => {
        setCreate(false);
    }

    return (
        <>
            <TableComponent 
                title="Mesas"
                columns={columns}
                rows={tables}
                handleDetails={handleTableRoute}
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