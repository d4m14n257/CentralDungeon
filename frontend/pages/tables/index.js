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
import { Message } from "@/contexts/MessageContext";

/* Zona horaria no mostrar 
    Doble click con tabla
*/

export const getServerSideProps = async () => {
    const result = await getter('1', 'tables/master/list');

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
    const shifhKey = useRef(false);
    const { mode } = useContext(ColorMode); 
    const { handleOpen, setMessage, setInfo } = useContext(Message) 
    const { open, handleCloseModal, handleOpenModal } = useModal();

    useEffect(() => {
        document.addEventListener('keydown', handleKeyDownDetected);
        document.addEventListener('keyup', handleKeyUpDetected)

        return () => {
            document.removeEventListener('keydown', handleKeyDownDetected);
            document.removeEventListener('keyup', handleKeyUpDetected);
          };
    }, [])
    
    const handleKeyDownDetected = (event) => {
        if(event.key == 'Shift') {
            if(!shifhKey.current) {
                setMessage('Cuidado, no se solicitara confirmacion para ninguna acción, mientras mantega pulsada la tecla.')
                handleOpen();
                setInfo(true);
                shifhKey.current = true;
            }
        }
    }

    const handleKeyUpDetected = (event) => {
        if(event.key == 'Shift') {
            if(shifhKey) {
                console.log("up :", event.key)
                shifhKey.current = false;
            }
        }
    }

    return (
        <Error>
            <Error.When isError={err}>
                <TableComponent 
                    title="Mis mesas"
                    columns={columns}
                    rows={table_list}
                    Actions={ActionButtonTable}
                    checkbox={false}
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