<%@ page contentType="text/html;charset=UTF-8" language="java"%>
<%@ taglib prefix="f" uri="http://java.sun.com/jsf/core"%>
<%@ taglib prefix="h" uri="http://java.sun.com/jsf/html"%>
<f:view>
	<!DOCTYPE html>
	<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Doctor Availability</title>
<script src="https://cdn.tailwindcss.com"></script>
<link rel="stylesheet"
	href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
<link rel="stylesheet"
	href="https://cdn.jsdelivr.net/npm/flatpickr/dist/flatpickr.min.css">
<style>
.date-card {
	min-width: 100px;
	flex-shrink: 0;
	transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.date-card:hover {
	transform: translateY(-2px);
}

.date-card:active {
	transform: translateY(0);
}

.time-slot {
	transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
	min-width: 100px;
	flex-shrink: 0;
	display: block;
	width: 100%;
}

.time-slot:hover {
	transform: translateY(-2px);
	box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
}

.time-slot:active {
	transform: scale(0.98);
}

.time-group {
	background: #f8fafc;
	border-left: 4px solid #3b82f6;
	transition: all 0.3s ease;
}

.date-scroll-container {
	overflow-x: auto;
	scrollbar-width: thin;
	padding-bottom: 1rem;
	-webkit-overflow-scrolling: touch;
}

.date-display-table>tbody {
	display: flex;
	flex-direction: row;
	gap: 0.75rem;
	flex-wrap: nowrap;
	padding: 0.5rem 0;
}

.date-display-table>tbody>tr {
	display: flex;
	flex-direction: column;
}

.time-slots-display-wrapper {
	display: flex;
	flex-wrap: wrap;
	gap: 0.5rem;
	align-items: flex-start;
}

.time-slots-table-component {
	width: auto;
}

.time-slots-table-component>tbody {
	display: flex;
	flex-direction: row;
	min-width: 100px;
	flex-shrink: 0;
	gap: 0.5rem;
	flex-wrap: wrap;
}

.time-slots-table-component>tbody>tr {
	display: contents;
}

.time-slots-table-component>tbody>tr>td {
	padding: 0;
}

@media ( max-width : 640px) {
	.date-card {
		min-width: 80px;
	}
	.time-slot {
		min-width: 80px;
		padding: 0.5rem 0.25rem;
		font-size: 0.875rem;
	}
	.date-picker-row {
		flex-direction: column;
		gap: 0.5rem;
	}
	.date-picker-message {
		margin-left: 0;
		margin-top: 0.5rem;
	}
}

.empty-state {
	background-color: #f9fafb;
	border: 1px dashed #d1d5db;
	border-radius: 0.5rem;
	transition: all 0.3s ease;
}

/* Ripple effect for buttons */
.ripple {
	position: relative;
	overflow: hidden;
}

.ripple-effect {
	position: absolute;
	border-radius: 50%;
	background: rgba(255, 255, 255, 0.4);
	transform: scale(0);
	animation: ripple 0.6s linear;
	pointer-events: none;
}

@
keyframes ripple {to { transform:scale(4);
	opacity: 0;
}

}

/* Flatpickr custom width */
.flatpickr-input {
	width: 120px !important;
}

/* Message container styling */
.message-container {
	flex: 1;
	margin-left: 1rem;
	min-height: 40px;
	display: flex;
	align-items: center;
}
</style>
</head>
<body class="bg-gray-50 min-h-screen p-4">
	<!-- Navbar -->
	<jsp:include page="NavBar.jsp" />
	<!-- Booking Loading Overlay -->
	<div id="bookingOverlay"
		style="display: none; position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(255, 255, 255, 0.8); z-index: 9999; justify-content: center; align-items: center;">
		<div class="text-center">
			<svg class="animate-spin h-10 w-10 text-blue-500 mx-auto"
				xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
			<circle class="opacity-25" cx="12" cy="12" r="10"
					stroke="currentColor" stroke-width="4"></circle>
			<path class="opacity-75" fill="currentColor"
					d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z"></path>
		</svg>
			<p class="mt-2 text-gray-700">Booking your appointment...</p>

		</div>
	</div>

	<div
		class="max-w-4xl mx-auto bg-white rounded-xl shadow-md overflow-hidden p-4 md:p-6 transition-all duration-300">
		<div class="flex items-center justify-between mb-4 md:mb-6">
			<h1
				class="text-xl md:text-2xl font-bold text-gray-800 transform transition-transform hover:-translate-y-0.5">
				<i class="fas fa-calendar-alt mr-2"></i> Book Appointment
			</h1>
			<div class="text-right">
				<span class=" text-lg md:text-xl font-semibold text-gray-700">
					<h:outputText
						value="#{doctorAvailabilityController.doctor.doctor_name}" />
				</span> <span class=" text-sm md:text-base text-blue-600 italic"> <h:outputText
						value="#{doctorAvailabilityController.doctor.specialization}" />
				</span>
			</div>
		</div>



		<h:form id="appointmentForm">
			<!-- Date Picker Row -->
			<div
				class="flex flex-row items-start justify-between gap-6 mb-4 md:mb-6">
				<!-- Date Picker Section (Left) -->
				<div class="flex flex-col flex-1">
					<h2 class="text-base md:text-lg font-semibold text-gray-700 mb-2">Select
						Date</h2>
					<div class="flex items-center gap-2">
						<h:inputText id="datePicker"
							value="#{doctorAvailabilityController.selectedDateInput}"
							styleClass="flatpickr-input px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500" />
						<h:commandButton id="datePickerSubmit" value="Go"
							styleClass="px-3 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
							action="#{doctorAvailabilityController.handleDateSelection}" />
						<div class="message-container date-picker-message">
							<h:messages globalOnly="true" styleClass="w-full"
								infoClass="p-2 bg-green-100 text-green-700 rounded border border-green-300 text-sm"
								errorClass="p-2 bg-red-100 text-red-700 rounded border border-red-300 text-sm" />
						</div>
					</div>
				</div>

				<!-- Doctor Availability Table (Right) -->
				<div class="w-50 ml-auto -mt-8">
					<h6 class="text-base md:text-lg font-semibold text-gray-700 ">Available
						Timings</h6>
					<h:dataTable
						value="#{doctorAvailabilityController.availabilityTiming}"
						var="tim"
						styleClass="w-full border border-gray-300 rounded-lg overflow-hidden shadow-sm">
						<h:column>
							<h:outputText value="#{tim}"
								styleClass="block px-4 pb-1 border-t border-gray-200 text-sm text-gray-700" />
						</h:column>
					</h:dataTable>
				</div>
			</div>




			<!-- Date Selection Cards -->
			<div class="mb-6 md:mb-8">
				<div class="date-scroll-container">
					<h:dataTable
						value="#{doctorAvailabilityController.groupedAvailabilityList}"
						var="day" styleClass="date-display-table">
						<h:column>
							<div
								class="date-card bg-white border rounded-lg p-2 md:p-3 text-center shadow-sm hover:shadow-md ripple">
								<h:commandButton value="#{day.displayDate}"
									action="#{doctorAvailabilityController.loadAvailableSlots}"
									styleClass="w-full font-medium text-blue-600 hover:text-blue-800 text-sm md:text-base">
									<f:setPropertyActionListener
										target="#{doctorAvailabilityController.selectedDate}"
										value="#{day.date}" />
								</h:commandButton>
								<div class="text-xs text-green-600 mt-1">
									<h:outputText value="#{day.totalSlots}" />
									slots
								</div>
							</div>
						</h:column>
					</h:dataTable>
				</div>
			</div>

			<!-- Time Slots -->
			<h:panelGroup
				rendered="#{not empty doctorAvailabilityController.selectedDate}">
				<div
					class="flex flex-col md:flex-row md:items-center md:justify-between mb-4 gap-2">
					<h2 class="text-base md:text-lg font-semibold text-gray-700">
						Available Time Slots for <span class="text-blue-600"> <h:outputText
								value="#{doctorAvailabilityController.selectedDate}">
								<f:convertDateTime pattern="EEEE, MMMM d, yyyy" />
							</h:outputText>
						</span>
					</h2>
				</div>

				<!-- Empty State -->
				<h:panelGroup
					rendered="#{empty doctorAvailabilityController.morningSlots and empty doctorAvailabilityController.afternoonSlots and empty doctorAvailabilityController.eveningSlots}">
					<div class="empty-state p-6 text-center mb-6 hover:shadow-sm">
						<i
							class="fas fa-calendar-times text-3xl text-gray-400 mb-3 transform transition-transform hover:scale-110"></i>
						<h3 class="text-lg font-medium text-gray-700 mb-1">No
							Available Slots</h3>
						<p class="text-gray-500">There are no available time slots for
							the selected date.</p>
						<p class="text-gray-500 text-sm mt-2">Please try another date
							or check back later.</p>
					</div>
				</h:panelGroup>

				<!-- Morning Slots -->
				<h:panelGroup
					rendered="#{not empty doctorAvailabilityController.morningSlots}">
					<div
						class="time-group p-3 md:p-4 rounded-lg mb-3 md:mb-4 hover:shadow-sm">
						<h3
							class="font-medium text-gray-800 mb-2 md:mb-3 flex items-center">
							<i
								class="fas fa-sun mr-2 transform transition-transform hover:rotate-12"></i>
							Morning Slots <span class="text-xs md:text-sm text-gray-500 ml-2">
								(<h:outputText
									value="#{doctorAvailabilityController.morningSlotCount}" />
								available)
							</span>
						</h3>
						<div class="time-slots-display-wrapper">
							<h:dataTable value="#{doctorAvailabilityController.morningSlots}"
								var="slot" styleClass="time-slots-table-component">
								<h:column>
									<h:commandButton value="#{slot.formattedTimeRange}"
										action="#{doctorAvailabilityController.bookAppointment}"
										onclick="return showBookingLoading();"
										styleClass="time-slot bg-blue-50 text-blue-700 border border-blue-200 
                rounded-md py-1 md:py-2 px-2 md:px-3 text-center hover:bg-blue-100 
                hover:border-blue-300 text-sm md:text-base ripple">

										<f:setPropertyActionListener
											target="#{doctorAvailabilityController.selectedAvailabilityId}"
											value="#{slot.availabilityId}" />
										<f:setPropertyActionListener
											target="#{doctorAvailabilityController.selectedSlotNumber}"
											value="#{slot.slotNumber}" />
									</h:commandButton>
								</h:column>
							</h:dataTable>
						</div>
					</div>
				</h:panelGroup>

				<!-- Afternoon Slots -->
				<h:panelGroup
					rendered="#{not empty doctorAvailabilityController.afternoonSlots}">
					<div
						class="time-group p-3 md:p-4 rounded-lg mb-3 md:mb-4 hover:shadow-sm">
						<h3
							class="font-medium text-gray-800 mb-2 md:mb-3 flex items-center">
							<i
								class="fas fa-cloud-sun mr-2 transform transition-transform hover:rotate-12"></i>
							Afternoon Slots <span
								class="text-xs md:text-sm text-gray-500 ml-2"> (<h:outputText
									value="#{doctorAvailabilityController.afternoonSlotCount}" />
								available)
							</span>
						</h3>
						<div class="time-slots-display-wrapper">
							<h:dataTable
								value="#{doctorAvailabilityController.afternoonSlots}"
								var="slot" styleClass="time-slots-table-component">
								<h:column>
									<h:commandButton value="#{slot.formattedTimeRange}"
										action="#{doctorAvailabilityController.bookAppointment}"
										onclick="return showBookingLoading();"
										styleClass="time-slot bg-blue-50 text-blue-700 border border-blue-200 
                rounded-md py-1 md:py-2 px-2 md:px-3 text-center hover:bg-blue-100 
                hover:border-blue-300 text-sm md:text-base ripple">

										<f:setPropertyActionListener
											target="#{doctorAvailabilityController.selectedAvailabilityId}"
											value="#{slot.availabilityId}" />
										<f:setPropertyActionListener
											target="#{doctorAvailabilityController.selectedSlotNumber}"
											value="#{slot.slotNumber}" />
									</h:commandButton>
								</h:column>
							</h:dataTable>
						</div>
					</div>
				</h:panelGroup>

				<!-- Evening Slots -->
				<h:panelGroup
					rendered="#{not empty doctorAvailabilityController.eveningSlots}">
					<div
						class="time-group p-3 md:p-4 rounded-lg mb-3 md:mb-4 hover:shadow-sm">
						<h3
							class="font-medium text-gray-800 mb-2 md:mb-3 flex items-center">
							<i
								class="fas fa-moon mr-2 transform transition-transform hover:rotate-12"></i>
							Evening Slots <span class="text-xs md:text-sm text-gray-500 ml-2">
								(<h:outputText
									value="#{doctorAvailabilityController.eveningSlotCount}" />
								available)
							</span>
						</h3>
						<div class="time-slots-display-wrapper">
							<h:dataTable value="#{doctorAvailabilityController.eveningSlots}"
								var="slot" styleClass="time-slots-table-component">
								<h:column>
									<h:commandButton value="#{slot.formattedTimeRange}"
										action="#{doctorAvailabilityController.bookAppointment}"
										onclick="return showBookingLoading();"
										styleClass="time-slot bg-blue-50 text-blue-700 border border-blue-200 
                rounded-md py-1 md:py-2 px-2 md:px-3 text-center hover:bg-blue-100 
                hover:border-blue-300 text-sm md:text-base ripple">

										<f:setPropertyActionListener
											target="#{doctorAvailabilityController.selectedAvailabilityId}"
											value="#{slot.availabilityId}" />
										<f:setPropertyActionListener
											target="#{doctorAvailabilityController.selectedSlotNumber}"
											value="#{slot.slotNumber}" />
									</h:commandButton>
								</h:column>
							</h:dataTable>
						</div>
					</div>
				</h:panelGroup>
			</h:panelGroup>
		</h:form>
	</div>

	<!-- JavaScript Libraries -->

	<!-- JavaScript Libraries -->
	<script src="https://cdn.jsdelivr.net/npm/flatpickr"></script>
	<script>
        document.addEventListener('DOMContentLoaded', function() {
            // Initialize date picker
            const datePickerElement = document.getElementById('appointmentForm:datePicker');
            
            // Set fixed width and placeholder
            datePickerElement.style.width = '120px';
            datePickerElement.setAttribute('placeholder', 'Select date');
            
            // Initialize flatpickr with placeholder
            flatpickr(datePickerElement, {
                dateFormat: "Y-m-d",
                minDate: "today",
                placeholder: "Select appointment date",
                disableMobile: false,
                onChange: function(selectedDates, dateStr) {
                    document.getElementById('appointmentForm:datePickerSubmit').click();
                }
            });

            // Add ripple effect to all elements with .ripple class
            document.querySelectorAll('.ripple').forEach(button => {
                button.addEventListener('click', function(e) {
                    const rect = this.getBoundingClientRect();
                    const x = e.clientX - rect.left;
                    const y = e.clientY - rect.top;
                    
                    const ripple = document.createElement('span');
                    ripple.classList.add('ripple-effect');
                    ripple.style.left = `${x}px`;
                    ripple.style.top = `${y}px`;
                    
                    this.appendChild(ripple);
                    
                    setTimeout(() => {
                        ripple.remove();
                    }, 600);
                });
            });
        });
    </script>
	<script src="https://cdn.jsdelivr.net/npm/flatpickr"></script>
	<script>
        document.addEventListener('DOMContentLoaded', function() {
            // Initialize date picker
            const datePickerElement = document.getElementById('appointmentForm:datePicker');
            
            // Set fixed width and placeholder
            datePickerElement.style.width = '120px';
            datePickerElement.setAttribute('placeholder', 'Select date');
            
            // Initialize flatpickr with placeholder
            flatpickr(datePickerElement, {
                dateFormat: "Y-m-d",
                minDate: "today",
                placeholder: "Select appointment date",
                disableMobile: false,
                onChange: function(selectedDates, dateStr) {
                    document.getElementById('appointmentForm:datePickerSubmit').click();
                }
            });

            // Add ripple effect to all elements with .ripple class
            document.querySelectorAll('.ripple').forEach(button => {
                button.addEventListener('click', function(e) {
                    const rect = this.getBoundingClientRect();
                    const x = e.clientX - rect.left;
                    const y = e.clientY - rect.top;
                    
                    const ripple = document.createElement('span');
                    ripple.classList.add('ripple-effect');
                    ripple.style.left = `${x}px`;
                    ripple.style.top = `${y}px`;
                    
                    this.appendChild(ripple);
                    
                    setTimeout(() => {
                        ripple.remove();
                    }, 600);
                });
            });
        });
        function showBookingLoading() {
    		document.getElementById("bookingOverlay").style.display = "flex";
    		return true;
    	}
    </script>
</body>
	</html>
</f:view>

