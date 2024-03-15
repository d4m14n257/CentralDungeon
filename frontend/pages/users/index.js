import ActionButtonUser from "@/components/users/ActionButtonUser";
import TableComponent from "@/components/TableComponent";

/*
    Por facilidad al admin, se mantendran los accesos rapidos a las estadisticas unicas, a pesar de que tenga un ciclo.
    Modificar como se ve la vista de informacion en usuarios para admin, tanto botones, aplicara tambien para las mesas.
    Los usuarios almaceran el ID del discord no el automatico por orm/sql/loqueseaxd.
*/

const users = 
{
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
            id: 'joined_table',
            label: 'Mesas inscritas',
            additional: 'Activas/Inscritos',
        },
        {
            id: 'master_table',
            label: 'Master en mesas',
            additional: 'Activas/Inscritos',
        },
        {
            id: 'comments',
            label: 'Comentarios',
        },
        {
            id: 'actions',
            label: 'Acciones',
        }
    ],
    rows: [
        {
            id: 1,
            name: 'Teshynil',
            discord: 'Teshynil#0001',
            joined_table: {
                inscrited_table: 10,
                active_table: 3,
                candidate_table: 4
            },
            master_table: {
                active_master_table: 3,
                created_master_table: 5,
            },
            comments: {
                positive_comments: 3,
                neutral_comments: 2,
                negative_comments: 7
            },
            karma: 1,
            country: 'Mexico',
            timezone: 'UTC-06:00'
        },
    ],
    minWidth: 170,
}

export default function Users () {
    return (
        <>
            <TableComponent
                title='Usuarios'
                columns={users.columns}
                rows={users.rows}
                minWidth={users.minWidth}
                Actions={ActionButtonUser}
            />
        </>
    );
}