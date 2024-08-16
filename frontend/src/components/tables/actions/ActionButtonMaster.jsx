import { useCallback, useContext, useEffect } from "react";

import { IconButton, Stack, Tooltip } from "@mui/material";

import { Confirm } from "@/contexts/ConfirmContext";
import { Message } from "@/contexts/MessageContext";

import DeleteIcon from '@mui/icons-material/Delete';
import VisibilityIcon from '@mui/icons-material/Visibility';
import { useModal } from "@/hooks/useModal";
import { deleter } from "@/api/deleter";
import UserModalInfo from "@/components/users/modal/UserModalInfo";


export default function ActionButtonMaster (props) {
    const { id, data, reloadActions } = props;
    const { open, handleOpenModalWithData, handleCloseModal, dataModal } = useModal();
    const { confirm, setMessage : deleteMessage } = useContext(Confirm)
    const { setStatus, setMessage, handleOpen } = useContext(Message)

    useEffect(() => {
        deleteMessage('¿Seguro desea remover a este usuario?');
    }, [])

    const handleOpenUser = () => {
        handleOpenModalWithData("algo");
    }

    const handleRemoveMaster = useCallback(async (event) => {
        try {
            if(!event.shiftKey) {
                await confirm()
                    .catch(() => {throw {canceled: true}})
            }


        }
        catch (err) {
            if(!err.canceled) {
                setStatus(err.status);
                setMessage(err.message);
                handleOpen();
            }
        }
    }, [])

    return (
        <>
            <Stack flexDirection='row' justifyContent='center'>
                <Tooltip
                    title='Ver usuario'
                >
                    <IconButton size='small' onClick={handleOpenUser}>
                        <VisibilityIcon fontSize="inherit"/>
                    </IconButton>
                </Tooltip>
                {data.master_type == "Owner" &&
                    <Tooltip
                        title='Remover'
                    >
                        <IconButton size='small'>
                            <DeleteIcon fontSize="inherit" color="error"/>
                        </IconButton>
                    </Tooltip>
                }
            </Stack>
            {open &&
                <UserModalInfo 
                    isOpen={open}
                    handleCloseModal={handleCloseModal}
                    user={dataModal}
                />
            }
        </>
    );
}