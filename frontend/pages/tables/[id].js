import { useState, useEffect, useCallback } from 'react';

import Typography from '@mui/material/Typography';
import Grid from '@mui/material/Grid';

import CardComponent from "@/components/general/CardComponent";
import TableComponent from '@/components/general/TableComponent';
import CardContent from '@mui/material/CardContent';

import ChangeCircleIcon from '@mui/icons-material/ChangeCircle';
import EditIcon from '@mui/icons-material/Edit';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';

import CardBodyFiles from '@/components/tables/CardBodyFiles';
import CardContentTable from '@/components/tables//CardContentTable';

import { getter } from '@/api/getter';
import { TablesInfo } from '@/normalize/models';
import { Error, ErrorMessage } from '@/components/info/HandlerError';
import { useModal } from '@/hooks/useModal';
import { MessageContext } from '@/contexts/MessageContext';
import EditModalTable from '@/components/tables/modals/EditModalTable';
import ActionEditButton from '@/components/tables/ActionEditButton';
import { ConfirmContext } from '@/contexts/ConfirmContext';
import { ShiftContext } from '@/contexts/ShiftContext';

/*
    TODO: Mesas que seas colapsables para jugadores pendientes y separar aceptadps de pendientes. 
*/

export const getServerSideProps = async (context) => {
    const { params } = context;
    const { id } = params;

    const result = await getter({ others: id, url: 'tables' });

    if(!result.status) {
        const data = {
            table: TablesInfo(result)
        }

        return {
            props: {
                id: id,
                ...data,
                err: false
            }
        }
    }
    else {
        return {
            props: {
                id: id,
                err_result: result,
                err: true
            }
        }
    }
} 

const columns = [
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
]

export default function Table (props) {
    const { id, table: tableServer, err, err_result } = props;
    const { open, dataModal, handleOpenModalWithData, handleCloseModal } = useModal();
    const [ table, setTable ] = useState(tableServer);
    const [useFiles, setUseFiles] = useState(true);
    const [statusTable, setStatusTable] = useState(table.status);

    useEffect(() => {
        // handleMenuTable();
    }, [statusTable])

    const handleTableReload = useCallback(async () => {
        const result = await getter({ others: id, url: 'tables' })

        if(!result.status) {
            const data = {
                table: TablesInfo(result)
            }

            setTable(data.table)
        }
        else
            location.reload();
    }, [])

    const handleChangeUseFiles = () => {
        setUseFiles(!useFiles);
    }

    const handleFeedback = (id) => {
        console.log(id)
    }

    const menu_files = [
        {
            name: 'Cambiar',
            Icon: ChangeCircleIcon,
            handleClickMenu: handleChangeUseFiles
        },
    ]

    const handleChangeStatusTable = () => {
        console.log('cambio xd')
    }

    return (
        <Grid
            container
            spacing={2} 
        >
            <Error>
                <Error.When isError={err}>
                    <ConfirmContext>
                        <MessageContext>
                            <ShiftContext>
                                <Grid 
                                    item 
                                    lg={8}
                                    xs={12}
                                >
                                    <CardComponent 
                                        title={table.name}
                                        subtitle={`Tipo de mesa: ${table.table_type}`}
                                        handleAction={handleOpenModalWithData}
                                        Actions={ActionEditButton}
                                        data={table}
                                    >
                                        <CardContentTable 
                                            table={table}

                                        />
                                    </CardComponent>
                                </Grid>
                                <Grid 
                                    item 
                                    lg={4}
                                    xs={12}
                                >
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
                                <Grid
                                    item
                                    lg={4}
                                    xs={12}
                                ></Grid>
                                <Grid
                                    item
                                    lg={4}
                                    xs={12}
                                ></Grid>
                                <Grid
                                    item
                                    lg={4}
                                    xs={12}
                                ></Grid>
                                {open &&
                                    <EditModalTable 
                                        isOpen={open}
                                        handleCloseModal={handleCloseModal}
                                        table={dataModal.current}
                                        reloadAction={handleTableReload}
                                    />
                                }
                            </ShiftContext>
                        </MessageContext>
                    </ConfirmContext>
                </Error.When>
                <Error.Else>
                    <ErrorMessage {...err_result}/>
                </Error.Else>
            </Error>
        </Grid>
    );
}


                                {/* <Grid item xs={12}>
                                {statusTable != 'Preparacion' ? 
                                    table.players.length ? 
                                        <TableComponent 
                                            title='Jugadores en mesa'
                                            columns={columns}
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
                                </Grid> */}