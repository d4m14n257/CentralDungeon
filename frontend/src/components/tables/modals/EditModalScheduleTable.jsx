import ModalBase from "@/components/general/ModalBase";
import EditScheduleForm from "@/forms/EditScheduleForm";
import { modal } from "@/styles/tables/modal";
import { Box, Typography } from "@mui/material";

export default function EditModalScheduleTable (props) {
    const { isOpen, handleCloseModal, reloadAction, schedule, table_id, closeConfirm } = props;

    return (
        <ModalBase
            isOpen={isOpen}
            handleCloseModal={handleCloseModal}
            closeConfirm
        >
            <Box sx={modal.body}>
                <Box sx={modal.header}>
                    <Typography variant='h5'>
                        Editar horario de la mesa
                    </Typography>
                </Box>
                <EditScheduleForm 
                    handleCloseModal={handleCloseModal}
                    reloadAction={reloadAction}
                    schedule={schedule}
                    table_id={table_id}
                />
            </Box>
        </ModalBase>
    );
}