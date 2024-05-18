import { useState } from "react";

import CardComponent from "@/components/general/CardComponent";
import TableComponent from '@/components/general/TableComponent';

import Grid from '@mui/material/Grid';

import ScheduleIcon from '@mui/icons-material/Schedule';
import AddCircleIcon from '@mui/icons-material/AddCircle';
import EditIcon from '@mui/icons-material/Edit';
import UploadIcon from '@mui/icons-material/Upload';

import CardContentTable from '@/components/tables//CardContentTable';
import ListComponent from '@/components/general/ListComponent';
import ListCataloguesTable from '@/components/tables/ListCataloguesTable';
import EditModalCatalogues from '@/components/tables/modals/EditAModalCatalogues';
import ListScheduleTable from '@/components/tables/ListScheduleTable';
import EditModalTable from '@/components/tables/modals/EditModalTable';
import ActionButtonDefault from '@/components/general/ActionButtonDefault';
import EditModalScheduleTable from '@/components/tables/modals/EditModalScheduleTable';
import ListFileTable from '@/components/tables/ListFilesTable';


import { useModal } from '@/hooks/useModal';
import { MessageContext } from '@/contexts/MessageContext';
import { ConfirmContext } from '@/contexts/ConfirmContext';
import { ShiftContext } from '@/contexts/ShiftContext';
import EditModalFilesTable from '@/components/tables/modals/EditModalFilesTable';

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

export function PreparationStatus (props) {
    const { id, table, handleTableReload } = props;
    const { open: openGeneral, dataModal: dataEdit, handleOpenModalWithData: handleOpenEditGeneral, handleCloseModal: handleCloseEditGeneral } = useModal();
    const { open: openTags, dataModal: dataTags, handleOpenModalWithData: handleOpenTagEdit, handleCloseModal: handleCloseTagEdit } = useModal();
    const { open: openSystems, dataModal: dataSystems, handleOpenModalWithData: handleOpenSystemEdit, handleCloseModal: handleCloseSystemEdit } = useModal();
    const { open: openPlatforms, dataModal: dataPlatfomrs, handleOpenModalWithData: handleOpenPlatformEdit, handleCloseModal: handleClosePlatformEdit } = useModal();
    const { open: openSchedule, dataModal: dataSchedule, handleOpenModalWithData: handleOpenScheduleEdit, handleCloseModal: handleCloseScheduleEdit } = useModal();
    const { open: openFiles, dataModal: dataFiles, handleOpenModalWithData : handleOpenFilesEdit, handleCloseModal: handleCloseFileEdit} = useModal();

    return (
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
                                description='Los archivos que seran requeridos para unirse a la mesa, en caso de ser necesario.'
                                Action={ActionButtonDefault}
                                handleAction={handleOpenFilesEdit}
                                dataAction={table.files}
                                titleAction='Agregar archivos.'
                                IconAction={UploadIcon}
                                hasCollapse
                            >    
                                <ListFileTable
                                    table_id={id}
                                    files={table.files}
                                    reloadAction={handleTableReload}
                                />
                            </ListComponent>
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
                                description='Horario de la mesa en base a la zona horaria de la mesa.'
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
                                    closeConfirm
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
                                    closeConfirm
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
                                    closeConfirm
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
                                    closeConfirm
                                />
                            }
                            {
                                <EditModalScheduleTable
                                    table_id={id}
                                    isOpen={openSchedule}
                                    handleCloseModal={handleCloseScheduleEdit}
                                    reloadAction={handleTableReload}
                                    schedule={dataSchedule.current}
                                    closeConfirm
                                />
                            }
                            {
                                <EditModalFilesTable 
                                    table_id={id}
                                    isOpen={openFiles}
                                    handleCloseModal={handleCloseFileEdit}
                                    reloadAction={handleTableReload}
                                    files={dataFiles.current}
                                    closeConfirm
                                />
                            }
                        </>
                    }
                </ShiftContext>
            </MessageContext>
        </ConfirmContext>
    );
}