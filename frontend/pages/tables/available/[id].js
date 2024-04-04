import Typography from '@mui/material/Typography';
import Grid from '@mui/material/Grid';

import CardComponent from "@/components/general/CardComponent";
import TableComponent from '@/components/TableComponent';

import CardBodyTable from '@/components/tables/CardBodyTable';

/* 
    Horario de juego de mesa (Semana, mas hora) (timezone)
    Tag catalogo / (Gore, safeword, D&D...)
    Los tag probablemnete se puedan crear fuera de admin
    Puede tener cero tags.
    La lista de jugadores puede ser privado.
    Horario puede ser privado despues de que la mesa alla sido iniciada (se deja la opcion al aire, en caso de ser necesario)
    Estados de la mesa
        - Preparacion (solo admin y master)
        - Abierto (todos la ven)
        - En proceso (Ya es privada para el publico)
            - En proceso abierto (sub proceso en caso de que el master requiera algo mas para su continuacion)
            - Pausado (En caso de que esta tenga un incidente pero por causas mayores se tuvo que detener)
        - Cancelado (Esta murio por varios factores, debe tener una razon de la misma)
        - Finalizado (Esta concluyo su ciclo de vida normal / Este proceso siempre es natural no puede ser forzado a menos que un
                      admin tenga mano en esto.) 
        Las mesas pueden reabrir la misma mesa, en terminos de front no cambia nada, en back se gaurda el historico de la misma.
        La relacion es uno a muchos para estos casos, pero normalemnte sera 1 a 1.

        Descargar por archivos
*/

const table = {
    master: 'Teshinyl mal escrito',
    name: 'Nombre de la mesa',
    descripction: 'Esta es una descripcion de una mesa que no existe solo para poner texto en esta cosa para y se vea jsjsjs',
    allowsmaster: 'Aqui seria una lista de cosas que el master hara durante la mesa, pero sera un texto explicativo.',
    startdate: '2023-12-21T19:30:00',
    timezone: 'UTC-02:00',
    requeriments: "un texto con los requisitos para leer si no existe este no deberia existir el texto el boton",
    files: ["/test/CV-Jorge-Damian-Dominguez-Jimenez-English.pdf"], //Este arreglo servira para identificar si tiene archivos o no, para habilitar el boton de descarga.
    tag: ['Gore', 'safeword', 'D&D'],
    system: "Nombre del sistema",
    schedule: ['Lunes - 18:30 UTC-06:00', 'Jueves - 19:30 UTC-06:00', 'Viernes - 14:30 UTC-06:00'],
    status: 'Abierto',
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
        }
    ],
}

export default function Available () {
    return (
        <Grid
            container
            spacing={2}
        >   
            <Grid
                item
                xs={12}
            >
                <CardComponent 
                    title={table.name}
                    subtitle={table.master}
                    additional={table.system}
                >
                    <CardBodyTable 
                        table={table}
                    />
                </CardComponent>
            </Grid>
            <Grid
                item
                xs={12}
            >
                {table.players.length ?
                    <TableComponent 
                        title='Jugadores en mesa'
                        columns={table.columns}
                        rows={table.players}
                        mt={5}
                    /> :
                    <Typography sx={{mt: 20, textAlign: 'center', opacity: '0.3', userSelect: 'none'}} variant='h3'>
                        La lista de jugadores es privada.
                    </Typography>
                }
            </Grid>
        </Grid>
    );
}