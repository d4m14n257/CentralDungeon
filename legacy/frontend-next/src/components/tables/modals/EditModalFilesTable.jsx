import { Box, Typography } from "@mui/material";

import ModalBase from "@/components/general/ModalBase";

import { modal } from "@/styles/tables/modal";
import EditFilesForm from "@/forms/EditFilesForm";

export default function EditModalFilesTable (props) {
    const { isOpen, handleCloseModal, reloadAction, files, table_id, closeConfirm } = props;

    return (
        <ModalBase
            isOpen={isOpen}
            handleCloseModal={handleCloseModal}
            closeConfirm
        >
            <Box sx={modal.body}>
                <Box sx={modal.header}>
                    <Typography variant='h5'>
                        Editar los archivos de la mesa.
                    </Typography>
                </Box>
                <EditFilesForm 
                    handleCloseModal={handleCloseModal}
                    reloadAction={reloadAction}
                    files={files}
                    table_id={table_id}
                />
            </Box>
        </ModalBase>
    );
}