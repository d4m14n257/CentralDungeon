import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import ButtonGroup from '@mui/material/ButtonGroup';

import ModalBase from "@/components/general/ModalBase";

import { modal } from '@/styles/tables/modal';

export default function RequestUserModalTable (props) {
    const { isOpen, handleCloseModal, id } = props;

    return (
        <ModalBase
            isOpen={isOpen}
            handleCloseModal={handleCloseModal}
        >
            <Box sx={modal.body}>
                <Box sx={modal.header}>
                    <Typography variant="h5">
                        Solicitud de Teshynil
                    </Typography>
                </Box>
                <Box sx={modal.content}>
                    {id}
                </Box>
                <Box sx={modal.footer}>
                    <ButtonGroup>
                        <Button
                            onClick={() => {
                        }}>
                            Aceptar
                        </Button>
                        <Button
                            onClick={() => {

                            }}
                        >  
                            Ver usuario
                        </Button>
                    </ButtonGroup>
                </Box>
            </Box>
        </ModalBase>
    );
}