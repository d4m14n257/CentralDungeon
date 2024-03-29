import { useRouter } from 'next/router';

import { useContext, useState } from 'react';

import Grid from '@mui/material/Grid';

import AddCircleIcon from '@mui/icons-material/AddCircle';

import { UserContext } from '@/context/UserContext';

import ListComponent from '@/components/ListComponent';
import ListBodyPlayer from '@/components/player/ListBodyPlayer';
import ListTableMasterBody from '@/components/master/ListTableMasterBody';
import ListRequestMasterBody from '@/components/master/ListRequestMasterBody';
import CreateModalTable from '@/components/tables/modals/CreateModalTable';
import { Error } from '@/components/Error';

import { getTablesIndex } from '@/api/getTablesIndex';

export const getServerSideProps = async () => {
    const result = await getTablesIndex("1");

    if(!result.status) {
        return {
            props: {
                public_tables: result.public_tables,
                first_class_tables: result.first_class_tables,
                joined_tables: result.joined_tables
            }
        }
    }
    else
        return {
            props: {
                err: result
            }
        }
}

const table_master = {
    table: [
        {
            id: 1,
            name: 'Nombre de una mesa',
            description: 'Esta es una descripcion que tu pudiste y no se me ocurrio que texto ponerle para hacer un relleno xd',
            players: 4,
            tag: ['D&D', 'Gore', 'safeword']
        },
        {
            id: 2,
            name: 'Nombre de una mesa',
            description: 'Esta es una descripcion que tu pudiste y no se me ocurrio que texto ponerle para hacer un relleno xd',
            players: 4,
            tag: ['D&D', 'Gore', 'safeword']
        },
        {
            id: 3,
            name: 'Nombre de una mesa',
            description: 'Esta es una descripcion que tu pudiste y no se me ocurrio que texto ponerle para hacer un relleno xd',
            players: 4,
            tag: ['D&D', 'Gore', 'safeword']
        },
        {
            id: 4,
            name: 'Nombre de una mesa',
            description: 'Esta es una descripcion que tu pudiste y no se me ocurrio que texto ponerle para hacer un relleno xd',
            players: 4,
            tag: ['D&D', 'Gore', 'safeword']
        },
        {
            id: 5,
            name: 'Nombre de una mesa',
            description: 'Esta es una descripcion que tu pudiste y no se me ocurrio que texto ponerle para hacer un relleno xd',
            players: 4,
            tag: ['D&D', 'Gore', 'safeword']
        }
    ],
    requests: [
        {
            id: 1,
            name_table: 'Nombre de la mesa solicitada',
            player_name: 'Teshinyl',
            date: '2023-12-21T19:34:47'
        },
        {
            id: 2,
            name_table: 'Nombre de la mesa solicitada',
            player_name: 'Teshinyl',
            date: '2023-12-21T19:34:47'
        },
        {
            id: 3,
            name_table: 'Nombre de la mesa solicitada',
            player_name: 'Teshinyl',
            date: '2023-12-21T19:34:47'
        },
        {
            id: 4,
            name_table: 'Nombre de la mesa solicitada',
            player_name: 'Teshinyl',
            date: '2023-12-21T19:34:47'
        },
        {
            id: 5,
            name_table: 'Nombre de la mesa solicitada',
            player_name: 'Teshinyl',
            date: '2023-12-21T19:34:47'
        }
    ]
}

export default function Dashboard (props) {
    const { public_tables, first_class_tables, joined_tables, err } = props;
    const router = useRouter();
    const { rol } = useContext(UserContext);


    const [create, setCreate] = useState(false);

    const handleTableSelect = ({id}) => {
        if(id) {
            if(id === 'public-table')
                router.push(`/public-table`)
            else if (id === 'first-class-table')
                router.push(`/first-class-table`)
            else if (id === 'joined-table')
                router.push(`/joined-table`)
        }
    }

    const handleOpenCreateTable = () => {
        setCreate(true)
    }

    const handleCloseCreateTable = () => {
        setCreate(false)
    }

    return (
        <Grid
            container
            spacing={2}
        >
            <Error>
                <Error.When>
                    dasdsa
                    {/* <ListComponent
                        xs={12}
                        lg={8}
                        title='Mesas publicas'
                        description='Mesas que se encunetran disponibles para todo publico.'
                    >   
                        <ListBodyPlayer
                            info={{name: 'Mesas publicas', id: 'public-table'}}
                            tables={public_tables}
                            handleTableSelect={handleTableSelect}
                            err={err}
                        />
                    </ListComponent> */}
                    {/* <ListComponent
                        xs={12}
                        lg={4}
                        title='Mesas de primera clase'
                        description='Mesas disponibles por temporada.'
                    >
                    <ListBodyPlayer
                            info={{name: 'Mesas de primera clase', id: 'first-class-table'}}
                            tables={first_class_tables}
                            handleTableSelect={handleTableSelect}
                    />
                    </ListComponent> */}
                    {/* <ListComponent
                        title= 'Mesas jugando'
                        description='Mesas donde estas jugando.'
                    >
                        <ListBodyPlayer
                            info={{name: 'Mesas jugando', id: 'joined-table'}}
                            tables={joined_tables}
                            handleTableSelect={handleTableSelect}
                        />
                    </ListComponent> */}
                </Error.When>
                <Error.Else err={err} />
            </Error>
        </Grid>
    );
}
    // else if(rol == 'Master') {
    //     return (
    //         <Grid
    //             container
    //             spacing={2}
    //         >
    //             <Grid
    //                 item
    //                 md={8}
    //                 xs={12}
    //             >
    //                 <ListComponent
    //                     title="Mis mesas"
    //                     description="Ultimas mesas creadas y activas."
    //                     action={{Icon: AddCircleIcon, handleClickButton: handleOpenCreateTable}}
    //                 >
    //                     <ListTableMasterBody
    //                         tables={table_master.table}
    //                     />
    //                 </ListComponent>
    //             </Grid>
    //             <Grid
    //                 item
    //                 md={4}
    //                 xs={12}
    //             >
    //                  <ListComponent
    //                     title="Solicitudes"
    //                     description="Ultimas solicitudes recibidas."
    //                 >
    //                     <ListRequestMasterBody
    //                         requests={table_master.requests}
    //                     />
    //                 </ListComponent>
    //             </Grid>
    //             {create &&
    //                 <CreateModalTable
    //                     isOpen={create}
    //                     handleCloseModal={handleCloseCreateTable}
    //                 />
    //             }
    //         </Grid>
    //     );
    // }
    // else {
    //     return (
    //         <Grid
    //             container
    //             spacing={2}
    //         >
    //             Admin
    //         </Grid>
    //     );
    // }