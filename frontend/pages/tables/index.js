import { useCallback, useContext, useEffect, useState } from "react";

import { IconButton, Tooltip } from '@mui/material';

import AddIcon from '@mui/icons-material/Add';

import TableComponent from "@/components/general/TableComponent";

import { global } from "@/styles/global";

import { ColorMode } from "@/contexts/ColorModeContext";

import CreateModalTable from "@/components/tables/modals/CreateModalTable";
import ActionButtonTable from "@/components/tables/ActionButtonTable";
import { getter } from "@/api/getter";
import { useModal } from "@/hooks/useModal";
import { Error, ErrorMessage } from "@/components/info/HandlerError";
import { Tables as TablesNormalize} from "@/normalize/models";
import { MessageContext } from "@/contexts/MessageContext";
import { ConfirmContext } from "@/contexts/ConfirmContext";
import { useRouter } from "next/router";
import { TABLES } from "@/constants/constants";
import { ShiftContext } from "@/contexts/ShiftContext";

export const getServerSideProps = async () => {
    const result = await getter({user_id: '1', url: 'tables/master/list'});

    if(!result.status) {
        const data = {
            table_list: TablesNormalize(result.table_list),
        }

        return {
            props: {
                ...data,
                err: false
            }
        }
    }
    else
        return {
            props: {
                err_result: result,
                err: true
            }
        }
}

const columns = [
    {
        id: 'name',
        label: 'Nombre',
    },
    {
        id: 'masters',
        label: 'Masters',
    },
    {
        id: 'players',
        label: 'Jugadores',
    },
    {
        id: 'table_type',
        label: 'Tipo de mesa',
    },
    {
        id: 'status',
        label: 'Estado',
    },
    {
        id: 'actions',
        label: 'Acciones',
    }
]

export default function Tables (props) {
    const { table_list, err, err_result } = props;
    const [ tableList, setTableList ] = useState(table_list);
    const { mode } = useContext(ColorMode); 
    const { open, handleCloseModal, handleOpenModal } = useModal();
    const router = useRouter();

    const handleTableRoute = (id) => {
        router.push(`${TABLES}/${id}`)
    }

    const handleTableListReload = useCallback(async () => {
        const result = await getter({user_id: '1', url: 'tables/master/list'});

        if(!result.status) {
            const data = {
                table_list: TablesNormalize(result.table_list),
            }

            setTableList([...data.table_list]);
        }
        else
            location.reload();
    }, [])

    return (
        <Error>
            <Error.When isError={err}>
                <ShiftContext>
                    <ConfirmContext>
                        <MessageContext>
                            <TableComponent 
                                title="Mis mesas"
                                columns={columns}
                                rows={tableList}
                                Actions={ActionButtonTable}
                                useCheckbox
                                doubleClick={handleTableRoute}
                                reloadTable={handleTableListReload}
                            />
                            <Tooltip
                                title='Crear mesa'
                            >
                                <IconButton 
                                    sx={{
                                        ...global.buttonFloat,
                                        backgroundColor: mode == 'dark' ? '#4b4b4b' : '#e8e8e8'
                                        }}
                                    onClick={handleOpenModal}
                                    >
                                    <AddIcon fontSize="large"/>
                                </IconButton>
                            </Tooltip>
                        </MessageContext>
                    </ConfirmContext>
                    {open && 
                        <CreateModalTable 
                            isOpen={open}
                            handleCloseModal={handleCloseModal}
                            reloadAction={handleTableListReload}
                        />
                    }
                </ShiftContext>
            </Error.When>
            <Error.Else>
                <ErrorMessage {...err_result} />
            </Error.Else>
        </Error>
    );
}