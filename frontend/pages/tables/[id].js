import { useState } from 'react';

import Typography from '@mui/material/Typography';
import Grid from '@mui/material/Grid';

import CardComponent from "@/components/general/CardComponent";
import TableComponent from '@/components/general/TableComponent';
import CardContent from '@mui/material/CardContent';

import EditIcon from '@mui/icons-material/Edit';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import ChangeCircleIcon from '@mui/icons-material/ChangeCircle';

import CardBodyFiles from '@/components/tables/CardBodyFiles';
import CreateModalTable from '@/components/tables/modals/CreateModalTable';
import CardContentTable from '@/components/tables//CardContentTable';
import ActionButtonMasterTable from '@/components/tables/ActionButtonMasterTable';

import { TableStateContext } from '@/contexts/TableStateContext';

/*
    TODO: Mesas que seas colapsables para jugadores pendientes y separar aceptadps de pendientes. 
*/

const table = {
    master: 'Teshinyl mal escrito',
    name: 'Nombre de la mesa',
    allowsmaster: 'Aqui seria una lista de cosas que el master hara durante la mesa, pero sera un texto explicativo.',
    descripction: 'Esta es una descripcion de una mesa que no existe solo para poner texto en esta cosa para y se vea jsjsjs',
    startdate: '2023-12-21T19:30:00',
    timezone: 'UTC-02:00',
    tag: ['Gore', 'safeword', 'D&D'],
    system: "Nombre del sistema",
    status: 'Preparacion', //Preparacion, Abierta, En proceso (Proceso o pausada), Cancelada, Finalizada
    platform: 'Roll20',
    duration: '2:30 horas',
    requeriments: "un texto con los requisitos para leer si no existe este no deberia existir el texto el boton",
    files: [
        {   
            name: "nombre_de_archivo",
            src: "/test/CV-Jorge-Damian-Dominguez-Jimenez-English.pdf",
        }
    ], //Este arreglo servira para identificar si tiene archivos o no, para habilitar el boton de descarga.
    schedule: ['Lunes - 18:30 UTC-06:00', 'Jueves - 19:30 UTC-06:00', 'Viernes - 14:30 UTC-06:00'],
    players: [
        {
            id: 1,
            name: 'Teshinyl xd',
            discord: 'Teshinyl xd#001', 
            status: 'Aceptado'
        },
        {
            id: 2,
            name: 'Teshynil',
            discord: 'Teshynil#001', 
            status: 'Pendiente'
        },
        {
            id: 3,
            name: 'd4m14n257',
            discord: 'd4m14n257#001', 
            status: 'Pendiente'
        },
        {
            id: 4,
            name: 'Daniel526',
            discord: 'Daniel526#001', 
            status: 'Pendiente'
        },
    ],
    columns: [
        {
            id: 'name',
            label: 'Nombre',
        },
        {
            id: 'discord',
            label: 'Discord',
        },
        {
            id: 'status',
            'label': 'Estado'
        },
        {
            id: 'actions',
            label: 'Acciones',
        }
    ],
}

export default function Table () {
    const [edit, setEdit] = useState(false);
    const [useFiles, setUseFiles] = useState(true);
    const [statusTable, setStatusTable] = useState(table.status)

    const handleOpenEditTable = () => {
        setEdit(true);
    }

    const handleCloseEditTable = () => {
        setEdit(false);
    }

    const handleChangeUseFiles = () => {
        setUseFiles(!useFiles);
    }

    const handleChangeStatusTable = () => {
        if(statusTable == 'Preparacion'){
            setStatusTable('Abierta');
            table.status = 'Abierta'
            setMenuTable([{
                name: 'Iniciar',
                Icon: PlayArrowIcon,
                handleClickMenu: handleChangeStatusTable
            }])
        }
    }

    const handleFeedback = (id) => {
        console.log(id)
    }

    const [menuTable, setMenuTable] = useState([
        {
            name: 'Editar',
            Icon: EditIcon,
            handleClickMenu: handleOpenEditTable
        },
        {
            name: 'Iniciar',
            Icon: PlayArrowIcon,
            handleClickMenu: handleChangeStatusTable
        }
    ])

    const menu_files = [
        {
            name: 'Cambiar',
            Icon: ChangeCircleIcon,
            handleClickMenu: handleChangeUseFiles
        },
    ]

    return (
        <TableStateContext.Provider value={{status: statusTable}}>
            <Grid
                container
                spacing={2} 
            >
                <Grid item xs={8}>
                    <CardComponent 
                        title={table.name}
                        subtitle={table.master}
                        menu={menuTable}
                    >
                        <CardContent>
                            <CardContentTable 
                                table={table}
                                haveSystem={true}
                            />
                        </CardContent>
                    </CardComponent>
                </Grid>
                <Grid item xs={4}>
                    <CardComponent
                        title="Archivos"
                        subtitle="Archivos requeridos para la mesa"
                        menu={statusTable == 'Preparacion' ? menu_files : null}
                    >
                        <CardBodyFiles 
                            files={table.files}
                            useFiles={useFiles}
                        />
                    </CardComponent>
                </Grid>
                <Grid item xs={12}>
                {statusTable != 'Preparacion' ? 
                    table.players.length ? 
                        <TableComponent 
                            title='Jugadores en mesa'
                            columns={table.columns}
                            rows={table.players}
                            handleFeedback={handleFeedback}
                            mt={5}
                            Actions={ActionButtonMasterTable}
                        /> :
                        <Typography sx={{mt: 20, textAlign: 'center', opacity: '0.3', userSelect: 'none'}} variant='h3'>
                            No hay jugadores registrados.
                        </Typography> :
                    <Typography sx={{mt: 20, textAlign: 'center', opacity: '0.3', userSelect: 'none'}} variant='h3'>
                        No se pueden registrar jugadores aun.
                    </Typography>
                }
                </Grid>
                {edit &&
                    <CreateModalTable 
                        isOpen={edit}
                        handleCloseModal={handleCloseEditTable}
                    />
                }
            </Grid>
        </TableStateContext.Provider>
    );
}