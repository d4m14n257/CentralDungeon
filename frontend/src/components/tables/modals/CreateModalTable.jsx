import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';

import ModalBase from "@/components/general/ModalBase";
import CreateTableForm from '@/forms/CreateTableForm';

import { modal } from "@/styles/tables/modal";
import { ConfirmContext } from '@/contexts/ConfirmContext';

export default function CreateModalTable (props) {
    const { isOpen, handleCloseModal, reloadAction, closeConfirm } = props;

    return (
        <ConfirmContext>
            <ModalBase
                isOpen={isOpen}
                handleCloseModal={handleCloseModal}
                closeConfirm={closeConfirm}
            >
                <Box sx={modal.body}>
                    <Box sx={modal.header}>
                        <Typography variant="h5">
                            Crear mesa
                        </Typography>
                    </Box>
                    <CreateTableForm
                        handleCloseModal={handleCloseModal}
                        reloadAction={reloadAction}
                    />
                </Box>
            </ModalBase>
        </ConfirmContext>
    );
}