import { useState, useCallback } from 'react';

import Grid from '@mui/material/Grid';

import CardComponent from "@/components/general/CardComponent";
import TableComponent from '@/components/general/TableComponent';

import ChangeCircleIcon from '@mui/icons-material/ChangeCircle';
import ScheduleIcon from '@mui/icons-material/Schedule';
import AddCircleIcon from '@mui/icons-material/AddCircle';
import EditIcon from '@mui/icons-material/Edit';

import CardBodyFiles from '@/components/tables/CardBodyFiles';
import CardContentTable from '@/components/tables//CardContentTable';

import ListComponent from '@/components/general/ListComponent';
import ListCataloguesTable from '@/components/tables/ListCataloguesTable';
import EditModalCatalogues from '@/components/tables/modals/EditAModalCatalogues';
import ListScheduleTable from '@/components/tables/ListScheduleTable';
import EditModalTable from '@/components/tables/modals/EditModalTable';
import { getter } from '@/api/getter';
import { TablesInfo } from '@/normalize/models';
import { Error, ErrorMessage } from '@/components/info/HandlerError';
import { useModal } from '@/hooks/useModal';
import { MessageContext } from '@/contexts/MessageContext';
import { ConfirmContext } from '@/contexts/ConfirmContext';
import { ShiftContext } from '@/contexts/ShiftContext';
import ActionButtonDefault from '@/components/general/ActionButtonDefault';
import EditModalScheduleTable from '@/components/tables/modals/EditModalSchedule';
import EditModalSchedule from '@/components/tables/modals/EditModalSchedule';


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
    const { open: openGeneral, dataModal: dataEdit, handleOpenModalWithData: handleOpenEditGeneral, handleCloseModal: handleCloseEditGeneral } = useModal();
    const { open: openTags, dataModal: dataTags, handleOpenModalWithData: handleOpenTagEdit, handleCloseModal: handleCloseTagEdit } = useModal();
    const { open: openSystems, dataModal: dataSystems, handleOpenModalWithData: handleOpenSystemEdit, handleCloseModal: handleCloseSystemEdit } = useModal();
    const { open: openPlatforms, dataModal: dataPlatfomrs, handleOpenModalWithData: handleOpenPlatformEdit, handleCloseModal: handleClosePlatformEdit } = useModal();
    const { open: openSchedule, dataModal: dataSchedule, handleOpenModalWithData: handleOpenScheduleEdit, handleCloseModal: handleCloseScheduleEdit } = useModal();
    const [ table, setTable ] = useState(tableServer);
    const [useFiles, setUseFiles] = useState(true);

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
                                {table &&
                                    <>
                                        <Grid 
                                            item 
                                            lg={8}
                                            xs={12}
                                        >   
                                            <CardComponent 
                                                title={table.name}
                                                titleAction='Editar datos generales'
                                                subtitle={`Tipo de mesa: ${table.table_type}`}
                                                handleAction={handleOpenEditGeneral}
                                                Actions={ActionButtonDefault}
                                                data={table}
                                                IconAction={EditIcon}
                                                hasCollapse
                                            >
                                                <CardContentTable 
                                                    table={table}

                                                />
                                            </CardComponent>
                                        </Grid>
                                        <ListComponent
                                            lg={4}
                                            xs={12}
                                            title='Archivos'
                                            description='Los archivos que seran requeridos para unirse, en caso de ser necesario.'
                                            hasCollapse
                                        >
                                            
                                        </ListComponent>
                                            {/* <CardComponent
                                                title="Archivos"
                                                subtitle="Los archivos que seran requeridos para unirse, en caso de ser necesario."
                                                menu={table.status == 'Preparacion' ? menu_files : null}
                                            >
                                                <CardBodyFiles 
                                                    files={table.files}
                                                    useFiles={useFiles}
                                                />
                                            </CardComponent> */}
                                        <ListComponent
                                            xs={12}
                                            lg={6}
                                            title='Tags de la mesa'
                                            description='Los tags que se encuentran en la mesa y estado.'
                                            Action={ActionButtonDefault}
                                            handleAction={handleOpenTagEdit}
                                            dataAction={table.tags}
                                            titleAction='Agregar tags.'
                                            IconAction={AddCircleIcon}
                                            hasCollapse
                                        >
                                            <ListCataloguesTable 
                                                table_id={id}
                                                name='tag'
                                                catalogue={table.tags}
                                                reloadAction={handleTableReload}
                                            />
                                        </ListComponent>
                                        <ListComponent
                                            xs={12}
                                            lg={6}
                                            title='Sistemas de la mesa.'
                                            description='Los sistemas que se utilizarian para la mesa.'
                                            Action={ActionButtonDefault}
                                            handleAction={handleOpenSystemEdit}
                                            dataAction={table.systems}
                                            titleAction='Agregar sistemas.'
                                            IconAction={AddCircleIcon}
                                            hasCollapse
                                        >
                                            <ListCataloguesTable 
                                                table_id={id}
                                                name='sistema'
                                                catalogue={table.systems}
                                                reloadAction={handleTableReload}
                                            />
                                        </ListComponent>
                                        <ListComponent
                                            xs={12}
                                            lg={6}
                                            title='Plataformas de la mesa'
                                            description='Las plataformas donde se jugara en la mesa.'
                                            Action={ActionButtonDefault}
                                            handleAction={handleOpenPlatformEdit}
                                            dataAction={table.platforms}
                                            titleAction='Agregar plataformas.'
                                            IconAction={AddCircleIcon}
                                            hasCollapse
                                        >
                                            <ListCataloguesTable 
                                                table_id={id}
                                                name='plataforma'
                                                catalogue={table.platforms}
                                                reloadAction={handleTableReload}
                                            />
                                        </ListComponent>
                                        <ListComponent
                                            xs={12}
                                            lg={6}
                                            title='Horario de la mesa'
                                            description='Hhorario de la mesa en base a la zona horaria de la mesa.'
                                            Action={ActionButtonDefault}
                                            handleAction={handleOpenScheduleEdit}
                                            dataAction={table.schedule}
                                            IconAction={ScheduleIcon}
                                            hasCollapse
                                        >
                                            <ListScheduleTable 
                                                table_id={id}
                                                schedule={table.schedule}
                                                reloadAction={handleTableReload}
                                                utc={table.timezone}
                                            />
                                        </ListComponent>
                                        {openGeneral &&
                                            <EditModalTable 
                                                isOpen={openGeneral}
                                                handleCloseModal={handleCloseEditGeneral}
                                                table={dataEdit.current}
                                                reloadAction={handleTableReload}
                                            />
                                        }
                                        {openTags && 
                                            <EditModalCatalogues
                                                table_id={id}
                                                type="Tags"
                                                isOpen={openTags}
                                                handleCloseModal={handleCloseTagEdit}
                                                reloadAction={handleTableReload}
                                                catalogue={dataTags.current}
                                            />
                                        }
                                        {openSystems && 
                                            <EditModalCatalogues
                                                table_id={id}
                                                type="Sistemas"
                                                isOpen={openSystems}
                                                handleCloseModal={handleCloseSystemEdit}
                                                reloadAction={handleTableReload}
                                                catalogue={dataSystems.current}
                                            />
                                        }
                                        {openPlatforms && 
                                            <EditModalCatalogues
                                                table_id={id}
                                                type="Plataformas"
                                                isOpen={openPlatforms}
                                                handleCloseModal={handleClosePlatformEdit}
                                                reloadAction={handleTableReload}
                                                catalogue={dataPlatfomrs.current}
                                            />
                                        }
                                        {
                                            <EditModalSchedule
                                                isOpen={openSchedule}
                                                handleCloseModal={handleCloseScheduleEdit}
                                                reloadAction={handleTableReload}
                                                schedule={dataSchedule.current}
                                            />
                                        }
                                    </>
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