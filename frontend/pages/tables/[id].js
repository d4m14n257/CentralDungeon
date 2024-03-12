import Typography from '@mui/material/Typography';
import Grid from '@mui/material/Grid';

import CardComponent from "@/components/CardComponent";
import TableComponent from '@/components/TableComponent';

import EditIcon from '@mui/icons-material/Edit';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import ChangeCircleIcon from '@mui/icons-material/ChangeCircle';

import CardBodyTableMaster from '@/components/tables/CardBodyTableMaster';
import CardBodyFiles from '@/components/tables/CardBodyFiles';

const table = {
    master: 'Teshinyl mal escrito',
    name: 'Nombre de la mesa',
    allowsmaster: 'Aqui seria una lista de cosas que el master hara durante la mesa, pero sera un texto explicativo.',
    descripction: 'Esta es una descripcion de una mesa que no existe solo para poner texto en esta cosa para y se vea jsjsjs',
    startdate: '2023-12-21T19:30:00',
    timezone: 'UTC-02:00',
    requeriments: "un texto con los requisitos para leer si no existe este no deberia existir el texto el boton",
    files: [
        {   
            name: "nombre_de_archivo",
            src: "/test/CV-Jorge-Damian-Dominguez-Jimenez-English.pdf",
        }
    ], //Este arreglo servira para identificar si tiene archivos o no, para habilitar el boton de descarga.
    tag: ['Gore', 'safeword', 'D&D'],
    system: "Nombre del sistema",
    schedule: ['Lunes - 18:30 UTC-06:00', 'Jueves - 19:30 UTC-06:00', 'Viernes - 14:30 UTC-06:00'],
    status: 'Preparacion',
    platform: 'Roll20',
    duration: 'One Shot',
    players: [
        {
            id: 1,
            name: 'Teshinyl xd',
            discord: 'Teshinyl xd#001', 
            status: 'Jugador'
        },
        {
            id: 2,
            name: 'Teshynil',
            discord: 'Teshynil#001', 
            status: 'Jugador'
        },
        {
            id: 3,
            name: 'd4m14n257',
            discord: 'd4m14n257#001', 
            status: 'Jugador'
        },
        {
            id: 4,
            name: 'Daniel526',
            discord: 'Daniel526#001', 
            status: 'Jugador'
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

const menu_table = [
    {
        name: 'Editar',
        Icon: EditIcon
    },
    {
        name: 'Iniciar',
        Icon: PlayArrowIcon
    }
];

const menu_files = [
    {
        name: 'Cambiar',
        Icon: ChangeCircleIcon
    },
]

export default function Table () {
    return (
        <Grid
            container
            spacing={2} 
        >
            <Grid item xs={8}>
                <CardComponent 
                    title={table.name}
                    subtitle={table.master}
                    menu={menu_table}
                >
                    <CardBodyTableMaster 
                        table={table}
                    />
                </CardComponent>
            </Grid>
            <Grid item xs={4}>
                <CardComponent
                    title="Archivos"
                    subtitle="Archivos requeridos para la mesa"
                    menu={menu_files}
                >
                    <CardBodyFiles 
                        files={table.files}
                    />
                </CardComponent>
            </Grid>
            <Grid item xs={12}>
            {table.status != 'Preparacion' ? 
                table.players.length ? 
                    <TableComponent 
                        title='Jugadores en mesa'
                        columns={table.columns}
                        rows={table.players}
                        mt={5}
                    /> :
                    <Typography sx={{mt: 20, textAlign: 'center', opacity: '0.3', userSelect: 'none'}} variant='h3'>
                        No hay jugadores registrados.
                    </Typography> :
                <Typography sx={{mt: 20, textAlign: 'center', opacity: '0.3', userSelect: 'none'}} variant='h3'>
                    No se pueden registrar jugadores aun.
                </Typography>
            }
            </Grid>
        </Grid>
    );
}