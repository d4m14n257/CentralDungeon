import ModalBase from "@/components/general/ModalBase";
import EditCataloguesForm from "@/forms/EditCataloguesForm";
import { modal } from "@/styles/tables/modal";
import { Box, Typography } from "@mui/material";

export default function EditModalCatalogues (props) {
    const { isOpen, handleCloseModal, reloadAction, catalogue, type, table_id } = props;

    return (
        <ModalBase
            isOpen={isOpen}
            handleCloseModal={handleCloseModal}
        > 
            <Box sx={modal.body}>
                <Box sx={modal.header}>
                    <Typography variant="h5">
                        Editar {type}
                    </Typography>
                </Box>
                <EditCataloguesForm 
                    data={catalogue}
                    handleCloseModal={handleCloseModal}
                    reloadAction={reloadAction}
                    type={type}
                    table_id={table_id}
                />
            </Box>
        </ModalBase>
    );
}