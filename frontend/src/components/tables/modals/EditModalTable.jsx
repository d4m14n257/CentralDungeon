import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';

import ModalBase from "@/components/general/ModalBase";

import { modal } from "@/styles/tables/modal";
import EditTableForm from '@/forms/EditTableForm';

export default function EditModalTable (props) {
    const { isOpen, handleCloseModal, reloadAction, table } = props;

    return (
        <ModalBase
            isOpen={isOpen}
            handleCloseModal={handleCloseModal}
        >
            <Box sx={modal.body}>
                <Box sx={modal.header}>
                    <Typography variant="h5">
                        Editar datos generales de la mesa
                    </Typography>
                </Box>
                <EditTableForm 
                    handleCloseModal={handleCloseModal}
                    reloadAction={reloadAction}
                    table={table}
                />
            </Box>
        </ModalBase>
    );
}