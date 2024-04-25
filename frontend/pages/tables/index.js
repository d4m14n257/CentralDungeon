import { useContext, useEffect, useRef } from "react";

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
import { Message, MessageContext } from "@/contexts/MessageContext";
import { ConfirmContext } from "@/contexts/ConfirmContext";
import { useRouter } from "next/router";
import { TABLES } from "@/constants/constants";

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
                err: result
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
    const { table_list, err } = props;
    const { mode } = useContext(ColorMode); 
    const { open, handleCloseModal, handleOpenModal } = useModal();
    const router = useRouter();

    const handleTableRoute = (id) => {
        router.push(`${TABLES}/${id}`)
    }

    return (
        <Error>
            <Error.When isError={err}>
                <ConfirmContext>
                    <MessageContext>
                        <TableComponent 
                            title="Mis mesas"
                            columns={columns}
                            rows={table_list}
                            Actions={ActionButtonTable}
                            useCheckbox
                            doubleClick={handleTableRoute}
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
                    />
                }
            </Error.When>
            <Error.Else>
                <ErrorMessage err={err}/>
            </Error.Else>
        </Error>
    );
}